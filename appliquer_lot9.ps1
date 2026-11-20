# Lot 9 - tests des services de subvention, de reinscription et de passage en classe
# Script 100 % ASCII. A lancer depuis la racine du projet :
#   powershell -ExecutionPolicy Bypass -File .\appliquer_lot9.ps1
$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path "pom.xml")) { throw "Lancez ce script depuis la racine du projet (pom.xml introuvable)." }

function Ecrire($chemin, $contenu) {
    New-Item -ItemType Directory -Force -Path (Split-Path $chemin) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $chemin), $contenu, $utf8)
    "  ecrit   : $(Split-Path $chemin -Leaf)"
}

$main = "src\main\java\com\fasodev\gestionscolaire"
$test = "src\test\java\com\fasodev\gestionscolaire"

# ---------- Prerequis : base redirigeable pour les tests ----------
$dc = "$main\database\DatabaseConnection.java"
if (-not (Test-Path $dc) -or -not (Select-String -Path $dc -SimpleMatch "utiliserBasePourTests" -Quiet)) {
    "ARRET : DatabaseConnection.java ne contient pas utiliserBasePourTests (lot 2 non applique)."
    exit 1
}
"Prerequis presents. Ecriture des tests..."

Ecrire "$test\services\SubventionEtatServiceTest.java" @'
package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.repositories.ConfigurationSubventionEtatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Subvention de l'Etat : reduction selon le palier, sur une vraie base temporaire. */
class SubventionEtatServiceTest {

    @TempDir
    Path dossier;

    private SubventionEtatService service;

    @BeforeEach
    void preparer() {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();
        service = new SubventionEtatService();
        service.mettreAJourConfiguration(50, 30, 2);   // CEP : 50 %, BEPC : 30 %, perte apres 2 redoublements
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private Etudiant eleve(boolean affecte, boolean subventionActive, String palier) {
        Etudiant e = new Etudiant();
        e.setAffecteEtat(affecte);
        e.setSubventionActive(subventionActive);
        e.setPalierSubvention(palier);
        return e;
    }

    @Test
    void un_eleve_non_affecte_n_a_pas_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(false, true, "CEP")));
    }

    @Test
    void une_subvention_perdue_ne_donne_plus_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, false, "BEPC")));
    }

    @Test
    void la_reduction_depend_du_palier() {
        assertEquals(50.0, service.getPourcentageReduction(eleve(true, true, "CEP")));
        assertEquals(30.0, service.getPourcentageReduction(eleve(true, true, "BEPC")));
    }

    @Test
    void un_palier_absent_ou_inconnu_ne_donne_pas_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, true, null)));
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, true, "BAC")));
    }

    @Test
    void les_frais_reels_appliquent_la_reduction() {
        assertEquals(50000.0, service.calculerFraisReels(eleve(true, true, "CEP"), 100000), 0.001);
        assertEquals(100000.0, service.calculerFraisReels(eleve(false, true, "CEP"), 100000), 0.001);
    }

    @Test
    void la_configuration_refuse_les_valeurs_invalides() {
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(-1, 30, 2));
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(50, 101, 2));
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(50, 30, 0));
    }

    @Test
    void la_configuration_est_enregistree() {
        service.mettreAJourConfiguration(40, 20, 3);

        ConfigurationSubventionEtatRepository.Config config = service.getConfiguration();

        assertEquals(40.0, config.reductionPalierCep);
        assertEquals(20.0, config.reductionPalierBepc);
        assertEquals(3, config.seuilRedoublementsPerte);
    }
}
'@

Ecrire "$test\services\ReinscriptionServiceIntegrationTest.java" @'
package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reinscriptions apres l'examen national, sur une vraie base temporaire.
 * Le refus passait par un statut que la base n'acceptait pas : ce test garde ce chemin sous surveillance.
 */
class ReinscriptionServiceIntegrationTest {

    @TempDir
    Path dossier;

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('3eme A', '3eme')");
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('2nde A', '2nde')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id, statut_scolarite) "
                + "VALUES ('Awa', 'Traore', 1, 'en_attente_reinscription')");
            st.execute("INSERT INTO resultats_examen_national (etudiant_id, classe_id, annee_scolaire, nom_examen, resultat) "
                + "VALUES (1, 1, '2026-2027', 'BEPC', 'admis')");
            st.execute("INSERT INTO reinscriptions (etudiant_id, resultat_examen_id, nouvelle_classe_id, annee_scolaire, statut) "
                + "VALUES (1, 1, 2, '2026-2027', 'en_attente')");
        }
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private String texte(String sql) throws SQLException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void confirmer_reinscrit_l_eleve_dans_la_nouvelle_classe() throws SQLException {
        ReinscriptionService.confirmerReinscription(1, "parent@exemple.com");

        assertEquals("confirmee", texte("SELECT statut FROM reinscriptions WHERE id = 1"));
        assertEquals("parent@exemple.com", texte("SELECT confirmee_par FROM reinscriptions WHERE id = 1"));
        assertEquals("2", texte("SELECT classe_id FROM etudiants WHERE id = 1"));
        assertEquals("actif", texte("SELECT statut_scolarite FROM etudiants WHERE id = 1"));
        assertEquals("R\u00c9INSCRIT", texte("SELECT statut_final FROM resultats_examen_national WHERE id = 1"));
    }

    @Test
    void refuser_marque_l_eleve_comme_parti() throws SQLException {
        ReinscriptionService.refuserReinscription(1, "demenagement");

        assertEquals("refusee", texte("SELECT statut FROM reinscriptions WHERE id = 1"));
        assertEquals("parti", texte("SELECT statut_scolarite FROM etudiants WHERE id = 1"));
        assertEquals("Refus r\u00e9inscription : demenagement",
            texte("SELECT commentaire_statut FROM etudiants WHERE id = 1"));
        assertEquals("1", texte("SELECT classe_id FROM etudiants WHERE id = 1"));   // classe inchangee
        assertEquals("REFUS\u00c9_R\u00c9INSCRIPTION",
            texte("SELECT statut_final FROM resultats_examen_national WHERE id = 1"));
    }

    @Test
    void on_ne_confirme_pas_deux_fois() {
        ReinscriptionService.confirmerReinscription(1, "parent@exemple.com");

        assertThrows(IllegalStateException.class,
            () -> ReinscriptionService.confirmerReinscription(1, "parent@exemple.com"));
    }

    @Test
    void on_ne_refuse_pas_une_reinscription_deja_confirmee() throws SQLException {
        ReinscriptionService.confirmerReinscription(1, "parent@exemple.com");

        assertThrows(IllegalStateException.class,
            () -> ReinscriptionService.refuserReinscription(1, "trop tard"));
        assertEquals("actif", texte("SELECT statut_scolarite FROM etudiants WHERE id = 1"));
    }

    @Test
    void une_reinscription_inconnue_est_refusee() {
        assertThrows(IllegalArgumentException.class,
            () -> ReinscriptionService.confirmerReinscription(999, "parent@exemple.com"));
        assertThrows(IllegalArgumentException.class,
            () -> ReinscriptionService.refuserReinscription(999, "raison"));
    }
}
'@

Ecrire "$test\services\PassageClasseIntegrationTest.java" @'
package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.models.ResultatPassage;
import com.fasodev.gestionscolaire.repositories.CalculMoyenneRepository;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.ResultatPassageRepository;
import com.fasodev.gestionscolaire.utils.AnneeScolaire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Passage en classe superieure sur une vraie base temporaire : statuts selon les seuils (10 et 8),
 * moyennes lues pour l'annee choisie, application du passage, deliberation.
 *
 * Eleves de la 6eme A : 1 (moyenne 12), 2 (moyenne 9), 3 (moyenne 5). Eleve 4 : en terminale.
 */
class PassageClasseIntegrationTest {

    @TempDir
    Path dossier;

    private String anneeA;
    private String anneeB;

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('6eme A', '6eme')");
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('5eme A', '5eme')");
            st.execute("INSERT INTO classes (nom, niveau, fin_de_parcours) VALUES ('Terminale A', 'terminale', 1)");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Awa', 'Traore', 1)");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Issa', 'Ouedraogo', 1)");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Moussa', 'Sawadogo', 1)");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Fanta', 'Kone', 3)");
        }

        anneeA = ConfigurationEcoleRepository.obtenirAnneeActive();
        anneeB = AnneeScolaire.suivante(anneeA);

        CalculMoyenneRepository calculs = new CalculMoyenneRepository();
        for (int trimestre = 1; trimestre <= 3; trimestre++) {
            int calculId = calculs.obtenirOuCreerCalculId(1, trimestre);
            calculs.sauvegarderMoyennes(calculId, 1, trimestre,
                List.of(resultat(1, 12.0), resultat(2, 9.0), resultat(3, 5.0)));
        }
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private MoyenneResultat resultat(int etudiantId, double moyenne) {
        MoyenneResultat r = new MoyenneResultat(etudiantId, "Eleve " + etudiantId, moyenne);
        r.setRang(etudiantId);
        return r;
    }

    private String texte(String sql) throws SQLException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Map<Integer, String> statuts(List<ResultatPassage> resultats) {
        Map<Integer, String> parEleve = new HashMap<>();
        for (ResultatPassage r : resultats) {
            parEleve.put(r.getEtudiantId(), r.getStatutCalcule());
        }
        return parEleve;
    }

    @Test
    void les_statuts_suivent_les_seuils() {
        Map<Integer, String> statuts = statuts(PassageClasseService.calculerPourClasse(1, anneeA));

        assertEquals("ADMIS", statuts.get(1));
        assertEquals("DELIBERATION", statuts.get(2));
        assertEquals("REDOUBLE", statuts.get(3));
    }

    @Test
    void le_passage_lit_les_moyennes_de_l_annee_choisie() {
        ConfigurationEcoleRepository.definirAnneeActive(anneeB);   // l'annee active n'est plus celle des moyennes

        Map<Integer, String> anneeDesMoyennes = statuts(PassageClasseService.calculerPourClasse(1, anneeA));
        Map<Integer, String> autreAnnee = statuts(PassageClasseService.calculerPourClasse(1, anneeB));

        assertEquals("ADMIS", anneeDesMoyennes.get(1));
        assertEquals("DELIBERATION", anneeDesMoyennes.get(2));
        assertEquals("REDOUBLE", autreAnnee.get(1));    // aucune moyenne en B : 0 de moyenne
    }

    @Test
    void un_admis_passe_dans_la_classe_superieure() throws SQLException {
        ResultatPassage r = new ResultatPassage(1, "Awa Traore", "6eme A", anneeA);
        r.setStatutFinal("ADMIS");

        PassageClasseService.appliquerPassage(r);

        assertEquals("2", texte("SELECT classe_id FROM etudiants WHERE id = 1"));
        assertEquals(2, r.getClasseSuivanteId());
    }

    @Test
    void un_redoublant_reste_dans_sa_classe_et_compte_un_redoublement() throws SQLException {
        ResultatPassage r = new ResultatPassage(3, "Moussa Sawadogo", "6eme A", anneeA);
        r.setStatutFinal("REDOUBLE");

        PassageClasseService.appliquerPassage(r);

        assertEquals("1", texte("SELECT classe_id FROM etudiants WHERE id = 3"));
        assertEquals("1", texte("SELECT nombre_redoublements FROM etudiants WHERE id = 3"));
    }

    @Test
    void un_admis_de_fin_de_parcours_sort_de_l_ecole() throws SQLException {
        ResultatPassage r = new ResultatPassage(4, "Fanta Kone", "Terminale A", anneeA);
        r.setStatutFinal("ADMIS");

        PassageClasseService.appliquerPassage(r);

        assertEquals("sorti", texte("SELECT statut_scolarite FROM etudiants WHERE id = 4"));
    }

    @Test
    void appliquer_un_passage_sans_decision_ou_pour_un_inconnu_est_refuse() {
        ResultatPassage sansDecision = new ResultatPassage(1, "Awa Traore", "6eme A", anneeA);
        ResultatPassage inconnu = new ResultatPassage(999, "Inconnu", "6eme A", anneeA);
        inconnu.setStatutFinal("ADMIS");

        assertThrows(IllegalStateException.class, () -> PassageClasseService.appliquerPassage(sansDecision));
        assertThrows(IllegalArgumentException.class, () -> PassageClasseService.appliquerPassage(inconnu));
    }

    @Test
    void la_deliberation_se_valide_une_fois_enregistree_et_seulement_si_elle_est_necessaire() {
        PassageClasseService.enregistrerResultats(PassageClasseService.calculerPourClasse(1, anneeA));

        int deliberation = ResultatPassageRepository.obtenirDeliberations(1, anneeA).get(0).getId();
        int admisDirect = ResultatPassageRepository.obtenirParEtudiant(1).get(0).getId();

        assertThrows(IllegalArgumentException.class,
            () -> PassageClasseService.validerDeliberation(deliberation, "PEUT-ETRE", "Directeur"));
        assertThrows(IllegalStateException.class,
            () -> PassageClasseService.validerDeliberation(admisDirect, "ADMIS", "Directeur"));

        PassageClasseService.validerDeliberation(deliberation, "ADMIS", "Directeur");

        assertEquals("ADMIS", ResultatPassageRepository.obtenirParId(deliberation).getStatutFinal());
    }
}
'@


"----- verification (tout doit etre True)"
foreach ($n in @("SubventionEtatServiceTest", "ReinscriptionServiceIntegrationTest", "PassageClasseIntegrationTest")) {
    "{0,-6} {1}" -f (Test-Path "$test\services\$n.java"), $n
}

"----- mvn test (resume ; 95 tests attendus si les lots precedents sont appliques)"
mvn test 2>&1 | Select-String "Tests run:.*Fail|BUILD|FAILURE|ERROR\]   " | Select-Object -Last 14
git --no-pager status -sb