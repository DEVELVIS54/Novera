package com.fasodev.novera.server.parents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Espace parents (lecture seule) : un parent se connecte avec l'identifiant et le mot de passe
 * remis par l'école, et consulte la scolarité de son enfant pour l'année scolaire active.
 */
@RestController
public class ParentController {

    public record Connexion(String identifiant, String motDePasse) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TokenService tokens;
    private final LoginThrottle throttle;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public ParentController(JdbcTemplate jdbc, ObjectMapper json, TokenService tokens, LoginThrottle throttle) {
        this.jdbc = jdbc;
        this.json = json;
        this.tokens = tokens;
        this.throttle = throttle;
    }

    @PostMapping("/api/parents/login")
    public Map<String, String> login(@RequestBody Connexion demande) {
        String identifiant = demande.identifiant() == null ? "" : demande.identifiant().trim();
        String motDePasse = demande.motDePasse() == null ? "" : demande.motDePasse();

        if (throttle.estBloque(identifiant)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives. Réessayez dans quelques minutes.");
        }

        List<Map<String, Object>> lignes = jdbc.queryForList("""
            SELECT record_id, payload::text AS payload
            FROM sync_records
            WHERE table_name = 'parents' AND deleted = FALSE AND payload->>'identifiant' = ?
            """, identifiant);

        if (lignes.isEmpty() || !motDePasseValide(lignes.get(0), motDePasse)) {
            throw refus(identifiant);
        }

        throttle.reinitialiser(identifiant);
        long parentId = ((Number) lignes.get(0).get("record_id")).longValue();
        return Map.of("token", tokens.creer(parentId));
    }

    @GetMapping("/api/parents/me")
    public Map<String, Object> moi(@RequestHeader(value = "Authorization", required = false) String autorisation) {
        long parentId = tokens.verifier(extraireJeton(autorisation));
        if (parentId < 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée");
        }

        JsonNode parent = lireLigne("parents", parentId);
        if (parent == null || parent.path("actif").asInt(0) != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Accès désactivé");
        }

        long etudiantId = parent.path("etudiant_id").asLong();
        JsonNode eleve = lireLigne("etudiants", etudiantId);
        if (eleve == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable");
        }
        long classeId = eleve.path("classe_id").asLong();
        JsonNode classe = lireLigne("classes", classeId);

        // Année affichée : l'année active de l'école (synchronisée), sinon la plus récente de l'élève.
        JsonNode config = lireLigne("configuration_ecole", 1);
        String annee = AnneeScolaireParents.choisir(
            config != null ? config.path("annee_scolaire_active").asText("") : "",
            anneesDesMoyennes(etudiantId));

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("eleve", Map.of(
            "nom", eleve.path("prenom").asText("") + " " + eleve.path("nom").asText(""),
            "matricule", eleve.path("matricule").asText(""),
            "classe", classe != null ? classe.path("nom").asText("") : ""));
        if (annee != null) {
            reponse.put("anneeScolaire", annee);
        }

        // Moyennes de l'année affichée (les lignes sans année, synchronisées avant la mise à jour,
        // sont rattachées à l'année affichée).
        List<Map<String, Object>> moyennes = new ArrayList<>();
        List<Map<String, Object>> lignes = jdbc.queryForList("""
            SELECT payload->>'trimestre' AS trimestre, payload->>'moyenne' AS moyenne, payload->>'rang' AS rang
            FROM sync_records
            WHERE table_name = 'moyennes_trimestrielles' AND deleted = FALSE
              AND (payload->>'etudiant_id')::bigint = ?
              AND (?::text IS NULL OR payload->>'annee_scolaire' IS NULL OR payload->>'annee_scolaire' = ?)
            ORDER BY payload->>'trimestre'
            """, etudiantId, annee, annee);
        for (Map<String, Object> l : lignes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("trimestre", l.get("trimestre"));
            m.put("moyenne", l.get("moyenne"));
            m.put("rang", l.get("rang"));
            moyennes.add(m);
        }
        reponse.put("moyennes", moyennes);

        Double totalPaye = jdbc.queryForObject("""
            SELECT COALESCE(SUM((payload->>'montant')::numeric), 0)::float8
            FROM sync_records
            WHERE table_name = 'paiements' AND deleted = FALSE
              AND (payload->>'etudiant_id')::bigint = ?
            """, Double.class, etudiantId);

        Double fraisClasse = jdbc.queryForObject("""
            SELECT COALESCE(SUM((payload->>'montant')::numeric), 0)::float8
            FROM sync_records
            WHERE table_name = 'frais_scolarite' AND deleted = FALSE
              AND (payload->>'classe_id')::bigint = ?
            """, Double.class, classeId);

        // Réduction « subvention de l'État » : même règle que le logiciel de bureau.
        JsonNode subvention = lireLigne("configuration_subvention_etat", 1);
        double pourcentage = ScolariteCalcul.pourcentageReduction(
            eleve.path("affecte_etat").asInt(0) == 1,
            eleve.path("subvention_active").asInt(1) == 1,
            eleve.path("palier_subvention").asText(""),
            subvention != null ? subvention.path("reduction_palier_cep").asDouble(0) : 0,
            subvention != null ? subvention.path("reduction_palier_bepc").asDouble(0) : 0);

        double paye = totalPaye != null ? totalPaye : 0.0;
        double fraisNormaux = fraisClasse != null ? fraisClasse : 0.0;
        double fraisDus = ScolariteCalcul.fraisDus(fraisNormaux, pourcentage);

        Map<String, Object> scolarite = new LinkedHashMap<>();
        scolarite.put("totalPaye", paye);
        scolarite.put("fraisDeLaClasse", fraisNormaux);
        scolarite.put("reductionPourcentage", pourcentage);
        scolarite.put("fraisDus", fraisDus);
        scolarite.put("resteAPayer", ScolariteCalcul.resteAPayer(fraisDus, paye));
        reponse.put("scolarite", scolarite);
        return reponse;
    }

    // ------------------------------------------------------------------

    private List<String> anneesDesMoyennes(long etudiantId) {
        return jdbc.queryForList("""
            SELECT DISTINCT payload->>'annee_scolaire'
            FROM sync_records
            WHERE table_name = 'moyennes_trimestrielles' AND deleted = FALSE
              AND (payload->>'etudiant_id')::bigint = ?
              AND payload->>'annee_scolaire' IS NOT NULL
            """, String.class, etudiantId);
    }

    private boolean motDePasseValide(Map<String, Object> ligne, String motDePasse) {
        try {
            JsonNode payload = json.readTree((String) ligne.get("payload"));
            if (payload.path("actif").asInt(0) != 1) return false;
            String hash = payload.path("password_hash").asText("");
            return !hash.isEmpty() && bcrypt.matches(motDePasse, hash);
        } catch (Exception e) {
            return false;
        }
    }

    private ResponseStatusException refus(String identifiant) {
        throttle.enregistrerEchec(identifiant);
        // Même message que l'identifiant existe ou non : on ne révèle pas les comptes.
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
    }

    private JsonNode lireLigne(String table, long recordId) {
        List<String> lignes = jdbc.queryForList("""
            SELECT payload::text FROM sync_records
            WHERE table_name = ? AND record_id = ? AND deleted = FALSE
            """, String.class, table, recordId);
        if (lignes.isEmpty()) return null;
        try {
            return json.readTree(lignes.get(0));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extraireJeton(String autorisation) {
        if (autorisation == null || !autorisation.startsWith("Bearer ")) return null;
        return autorisation.substring(7).trim();
    }
}