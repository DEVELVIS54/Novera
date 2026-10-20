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
 * remis par l'école, et consulte la scolarité de son enfant.
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

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("eleve", Map.of(
            "nom", eleve.path("prenom").asText("") + " " + eleve.path("nom").asText(""),
            "matricule", eleve.path("matricule").asText(""),
            "classe", classe != null ? classe.path("nom").asText("") : ""));

        List<Map<String, Object>> moyennes = new ArrayList<>();
        List<Map<String, Object>> lignes = jdbc.queryForList("""
            SELECT payload->>'trimestre' AS trimestre, payload->>'moyenne' AS moyenne, payload->>'rang' AS rang
            FROM sync_records
            WHERE table_name = 'moyennes_trimestrielles' AND deleted = FALSE
              AND (payload->>'etudiant_id')::bigint = ?
            ORDER BY payload->>'trimestre'
            """, etudiantId);
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
        reponse.put("scolarite", Map.of(
            "totalPaye", totalPaye != null ? totalPaye : 0.0,
            "fraisDeLaClasse", fraisClasse != null ? fraisClasse : 0.0));

        return reponse;
    }

    // ------------------------------------------------------------------

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
