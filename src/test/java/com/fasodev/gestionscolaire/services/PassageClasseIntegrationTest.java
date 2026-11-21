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