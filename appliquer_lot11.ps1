# Lot 11 - tests de bout en bout du serveur sur un PostgreSQL embarque (sans Docker ni installation)
# Script 100 % ASCII. A lancer depuis la racine du projet :
#   powershell -ExecutionPolicy Bypass -File .\appliquer_lot11.ps1
$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)
if (-not (Test-Path "novera-server\pom.xml")) { throw "Lancez ce script depuis la racine du projet (novera-server introuvable)." }

$pom  = "novera-server\pom.xml"
$ctrl = "novera-server\src\main\java\com\fasodev\novera\server\parents\ParentController.java"
$test = "novera-server\src\test\java\com\fasodev\novera\server\ServeurIntegrationTest.java"

# ---------- Prerequis : le serveur doit contenir les corrections des lots 4 et 5 ----------
$attendus = @(
  @($ctrl, "anneesDesMoyennes"),
  @($ctrl, "Double.class, classeId, annee, annee"),
  @("novera-server\src\main\java\com\fasodev\novera\server\records\ChampsSensibles.java", "password_hash")
)
$manquants = @()
foreach ($c in $attendus) {
    if (-not (Test-Path $c[0]) -or -not (Select-String -Path $c[0] -SimpleMatch $c[1] -Quiet)) { $manquants += "$(Split-Path $c[0] -Leaf) [$($c[1])]" }
}
if ($manquants.Count -gt 0) { "ARRET : corrections du serveur absentes (lots 4 et 5) :"; $manquants; exit 1 }
"Prerequis presents. Application du lot 11..."

# ---------- 1. pom.xml : PostgreSQL embarque pour les tests ----------
$p = Join-Path (Get-Location).Path $pom
$brut = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
if ($brut.Contains("embedded-postgres")) { "  deja ok : pom.xml" }
else {
    $crlf = $brut.Contains("`r`n")
    $t = $brut.Replace("`r`n", "`n")
    if ([regex]::Matches($t, '</dependencies>').Count -ne 1) { throw "pom.xml : balise </dependencies> introuvable ou multiple." }
    $dep = @'
        <!-- Tests : vrai PostgreSQL demarre dans le test, sans installation ni Docker -->
        <dependency>
            <groupId>io.zonky.test</groupId>
            <artifactId>embedded-postgres</artifactId>
            <version>2.0.7</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
'@
    $t = $t.Replace('</dependencies>', $dep.TrimStart())
    if ($crlf) { $t = $t.Replace("`n", "`r`n") }
    [System.IO.File]::WriteAllText($p, $t, $utf8)
    "  modifie : pom.xml"
}

# ---------- 2. Le test ----------
New-Item -ItemType Directory -Force -Path (Split-Path $test) | Out-Null
$contenu = @'
package com.fasodev.novera.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de bout en bout du serveur sur un vrai PostgreSQL embarque (aucune installation ni Docker) :
 * le desktop envoie un lot de synchronisation, le serveur l'enregistre, un parent se connecte
 * et consulte la scolarite de son enfant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServeurIntegrationTest {

    private static final String CLE_API = "cle-api-de-test-0123456789abcdef";
    private static final String SECRET = "secret-de-signature-de-test-0123456789";
    private static final String MOT_DE_PASSE = "Parent2026";
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private static final EmbeddedPostgres POSTGRES = demarrerPostgres();

    private static EmbeddedPostgres demarrerPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("PostgreSQL embarque impossible a demarrer", e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registre) {
        registre.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registre.add("spring.datasource.username", () -> "postgres");
        registre.add("spring.datasource.password", () -> "postgres");
        registre.add("novera.api-key", () -> CLE_API);
        registre.add("novera.token-secret", () -> SECRET);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void viderLaBase() {
        jdbc.update("DELETE FROM sync_records");
    }

    // ---------------------------------------------------------------- outils

    private record Reponse(int statut, JsonNode corps) {}

    private Reponse appeler(String methode, String chemin, Object corps, Map<String, String> entetes) throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
        entetes.forEach(requete::header);
        if (corps != null) {
            requete.header("Content-Type", "application/json");
            requete.method(methode, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corps)));
        } else {
            requete.method(methode, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> reponse = http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode noeud = reponse.body().isBlank() ? json.nullNode() : json.readTree(reponse.body());
        return new Reponse(reponse.statusCode(), noeud);
    }

    private static Map<String, Object> m(Object... paires) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            resultat.put((String) paires[i], paires[i + 1]);
        }
        return resultat;
    }

    private static Map<String, Object> op(long queueId, String table, String operation, long recordId,
                                          Map<String, Object> payload) {
        Map<String, Object> resultat = m("queueId", queueId, "table", table, "operation", operation, "recordId", recordId);
        if (payload != null) {
            resultat.put("payload", payload);
        }
        return resultat;
    }

    private Reponse envoyer(List<Map<String, Object>> operations, boolean avecCle) throws Exception {
        Map<String, String> entetes = avecCle ? Map.of("X-Api-Key", CLE_API) : Map.of();
        return appeler("POST", "/api/sync/batch", m("deviceId", "poste-test", "operations", operations), entetes);
    }

    /** Une ecole avec un eleve subventionne (CEP, 50 %), son parent, deux annees de donnees. */
    private void chargerLeScenario() throws Exception {
        List<Map<String, Object>> operations = List.of(
            op(1, "classes", "INSERT", 1, m("id", 1, "nom", "6eme A", "niveau", "6eme")),
            op(2, "etudiants", "INSERT", 1, m("id", 1, "prenom", "Awa", "nom", "Traore", "matricule", "M001",
                "classe_id", 1, "affecte_etat", 1, "palier_subvention", "CEP", "subvention_active", 1)),
            op(3, "parents", "INSERT", 1, m("id", 1, "etudiant_id", 1, "identifiant", "LSJK-6A-0001",
                "password_hash", BCRYPT.encode(MOT_DE_PASSE), "actif", 1)),
            op(4, "configuration_ecole", "INSERT", 1, m("id", 1, "nom", "Ecole test", "annee_scolaire_active", "2026-2027")),
            op(5, "configuration_subvention_etat", "INSERT", 1,
                m("id", 1, "reduction_palier_cep", 50, "reduction_palier_bepc", 30, "seuil_redoublements_perte", 2)),
            op(6, "moyennes_trimestrielles", "INSERT", 1, m("id", 1, "etudiant_id", 1, "classe_id", 1,
                "trimestre", 1, "moyenne", 14.5, "rang", 2, "annee_scolaire", "2026-2027")),
            op(7, "moyennes_trimestrielles", "INSERT", 2, m("id", 2, "etudiant_id", 1, "classe_id", 1,
                "trimestre", 1, "moyenne", 9.0, "rang", 5, "annee_scolaire", "2025-2026")),
            op(8, "paiements", "INSERT", 1, m("id", 1, "etudiant_id", 1, "montant", 20000, "annee_scolaire", "2026-2027")),
            op(9, "paiements", "INSERT", 2, m("id", 2, "etudiant_id", 1, "montant", 99999, "annee_scolaire", "2025-2026")),
            op(10, "frais_scolarite", "INSERT", 1, m("id", 1, "classe_id", 1, "montant", 100000, "annee_scolaire", "2026-2027")),
            op(11, "frais_scolarite", "INSERT", 2, m("id", 2, "classe_id", 1, "montant", 500000, "annee_scolaire", "2025-2026")));

        Reponse reponse = envoyer(operations, true);
        assertEquals(200, reponse.statut());
        assertEquals(11, reponse.corps().get("accepted").size());
    }

    private Reponse connexion(String identifiant, String motDePasse) throws Exception {
        return appeler("POST", "/api/parents/login", m("identifiant", identifiant, "motDePasse", motDePasse), Map.of());
    }

    private String jetonDuParent() throws Exception {
        chargerLeScenario();
        Reponse reponse = connexion("LSJK-6A-0001", MOT_DE_PASSE);
        assertEquals(200, reponse.statut());
        return reponse.corps().get("token").asText();
    }

    private Reponse moi(String jeton) throws Exception {
        return appeler("GET", "/api/parents/me", null, Map.of("Authorization", "Bearer " + jeton));
    }

    // ---------------------------------------------------------------- synchronisation

    @Test
    void le_serveur_enregistre_un_lot_du_desktop_et_l_envoi_est_rejouable() throws Exception {
        List<Map<String, Object>> lot = List.of(
            op(1, "classes", "INSERT", 1, m("id", 1, "nom", "6eme A")),
            op(2, "classes", "INSERT", 2, m("id", 2, "nom", "5eme A")));

        Reponse premiere = envoyer(lot, true);
        Reponse rejeu = envoyer(lot, true);

        assertEquals(200, premiere.statut());
        assertEquals(2, premiere.corps().get("accepted").size());
        assertEquals(2, rejeu.corps().get("accepted").size());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sync_records", Integer.class));
    }

    @Test
    void sans_cle_d_acces_le_lot_est_refuse() throws Exception {
        Reponse reponse = envoyer(List.of(op(1, "classes", "INSERT", 1, m("id", 1))), false);

        assertEquals(401, reponse.statut());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sync_records", Integer.class));
    }

    @Test
    void une_operation_invalide_est_refusee_avec_son_motif() throws Exception {
        Reponse reponse = envoyer(List.of(
            op(1, "table_inconnue", "INSERT", 1, m("id", 1)),
            op(2, "classes", "INSERT", 1, m("id", 1, "nom", "6eme A"))), true);

        assertEquals(1, reponse.corps().get("rejected").size());
        assertEquals(1, reponse.corps().get("rejected").get(0).get("queueId").asInt());
        assertEquals(1, reponse.corps().get("accepted").size());
        assertEquals(2, reponse.corps().get("accepted").get(0).asInt());
    }

    @Test
    void une_suppression_retire_la_ligne_de_la_lecture() throws Exception {
        envoyer(List.of(op(1, "classes", "INSERT", 1, m("id", 1, "nom", "6eme A"))), true);
        envoyer(List.of(op(2, "classes", "DELETE", 1, null)), true);

        Reponse lecture = appeler("GET", "/api/records/classes", null, Map.of("X-Api-Key", CLE_API));

        assertEquals(200, lecture.statut());
        assertEquals(0, lecture.corps().size());
    }

    // ---------------------------------------------------------------- securite

    @Test
    void l_api_de_lecture_ne_renvoie_jamais_le_hash_des_mots_de_passe() throws Exception {
        chargerLeScenario();

        Reponse lecture = appeler("GET", "/api/records/parents", null, Map.of("X-Api-Key", CLE_API));
        String contenu = lecture.corps().get(0).get("payload").asText();

        assertEquals(200, lecture.statut());
        assertTrue(contenu.contains("LSJK-6A-0001"));
        assertFalse(contenu.contains("password_hash"));
        assertFalse(contenu.contains("$2a$"));
    }

    @Test
    void sans_jeton_valide_l_espace_parents_est_refuse() throws Exception {
        assertEquals(401, appeler("GET", "/api/parents/me", null, Map.of()).statut());
        assertEquals(401, moi("jeton.invalide.signature").statut());
    }

    @Test
    void un_mauvais_mot_de_passe_est_refuse_puis_le_compte_est_bloque() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(401, connexion("BLOQUE-0001", "faux" + i).statut());
        }

        assertEquals(429, connexion("BLOQUE-0001", "faux").statut());
    }

    @Test
    void un_parent_desactive_ne_peut_plus_se_connecter() throws Exception {
        envoyer(List.of(op(1, "parents", "INSERT", 1, m("id", 1, "etudiant_id", 1, "identifiant", "LSJK-6A-0002",
            "password_hash", BCRYPT.encode(MOT_DE_PASSE), "actif", 0))), true);

        assertEquals(401, connexion("LSJK-6A-0002", MOT_DE_PASSE).statut());
    }

    // ---------------------------------------------------------------- espace parents

    @Test
    void le_parent_voit_l_annee_active_avec_le_solde_et_la_subvention() throws Exception {
        JsonNode donnees = moi(jetonDuParent()).corps();

        assertEquals("2026-2027", donnees.get("anneeScolaire").asText());
        assertEquals("Awa Traore", donnees.get("eleve").get("nom").asText());
        assertEquals("6eme A", donnees.get("eleve").get("classe").asText());
        assertEquals(1, donnees.get("moyennes").size());
        assertEquals(14.5, Double.parseDouble(donnees.get("moyennes").get(0).get("moyenne").asText()), 0.001);

        JsonNode scolarite = donnees.get("scolarite");
        assertEquals(20000.0, scolarite.get("totalPaye").asDouble(), 0.001);
        assertEquals(100000.0, scolarite.get("fraisDeLaClasse").asDouble(), 0.001);
        assertEquals(50.0, scolarite.get("reductionPourcentage").asDouble(), 0.001);
        assertEquals(50000.0, scolarite.get("fraisDus").asDouble(), 0.001);
        assertEquals(30000.0, scolarite.get("resteAPayer").asDouble(), 0.001);
    }

    @Test
    void une_subvention_perdue_supprime_la_reduction() throws Exception {
        String jeton = jetonDuParent();
        envoyer(List.of(op(30, "etudiants", "UPDATE", 1, m("id", 1, "prenom", "Awa", "nom", "Traore",
            "matricule", "M001", "classe_id", 1, "affecte_etat", 1, "palier_subvention", "CEP",
            "subvention_active", 0))), true);

        JsonNode scolarite = moi(jeton).corps().get("scolarite");

        assertEquals(0.0, scolarite.get("reductionPourcentage").asDouble(), 0.001);
        assertEquals(100000.0, scolarite.get("fraisDus").asDouble(), 0.001);
        assertEquals(80000.0, scolarite.get("resteAPayer").asDouble(), 0.001);
    }

    @Test
    void changer_l_annee_active_change_moyennes_paiements_et_frais() throws Exception {
        String jeton = jetonDuParent();
        envoyer(List.of(op(31, "configuration_ecole", "UPDATE", 1,
            m("id", 1, "nom", "Ecole test", "annee_scolaire_active", "2025-2026"))), true);

        JsonNode donnees = moi(jeton).corps();
        JsonNode scolarite = donnees.get("scolarite");

        assertEquals("2025-2026", donnees.get("anneeScolaire").asText());
        assertEquals(9.0, Double.parseDouble(donnees.get("moyennes").get(0).get("moyenne").asText()), 0.001);
        assertEquals(99999.0, scolarite.get("totalPaye").asDouble(), 0.001);
        assertEquals(500000.0, scolarite.get("fraisDeLaClasse").asDouble(), 0.001);
        assertEquals(250000.0, scolarite.get("fraisDus").asDouble(), 0.001);
        assertEquals(150001.0, scolarite.get("resteAPayer").asDouble(), 0.001);
    }
}
'@
[System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $test), $contenu, $utf8)
"  ecrit   : ServeurIntegrationTest.java"

"----- verification"
"{0,-6} dependance dans pom.xml" -f (Select-String -Path $pom -SimpleMatch "embedded-postgres" -Quiet)
"{0,-6} test" -f (Test-Path $test)

"----- mvn test du serveur (42 attendus ; le premier lancement telecharge PostgreSQL embarque)"
Push-Location novera-server
mvn test 2>&1 | Select-String "Tests run:.*Fail|BUILD|FAILURE|ERROR\]   |Could not|resolve" | Select-Object -Last 16
Pop-Location
git --no-pager status -sb