package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.ResultatPassage;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perte automatique de la subvention de l'Etat : un eleve affecte la perd quand son nombre de
 * redoublements atteint le seuil configure. Eleve 1 : affecte (palier CEP). Eleve 2 : non affecte.
 */
class SubventionPerteIntegrationTest {

    @TempDir
    Path dossier;

    private SubventionEtatService subvention;
    private String annee;

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('6eme A', '6eme')");
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('5eme A', '5eme')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id, affecte_etat, palier_subvention) "
                + "VALUES ('Awa', 'Traore', 1, 1, 'CEP')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Issa', 'Ouedraogo', 1)");
        }

        subvention = new SubventionEtatService();
        subvention.mettreAJourConfiguration(50, 30, 2);   // perte apres 2 redoublements
        annee = ConfigurationEcoleRepository.obtenirAnneeActive();
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

    private void redoubler(int etudiantId) {
        ResultatPassage r = new ResultatPassage(etudiantId, "Eleve " + etudiantId, "6eme A", annee);
        r.setStatutFinal("REDOUBLE");
        PassageClasseService.appliquerPassage(r);
    }

    @Test
    void la_subvention_est_perdue_au_seuil_de_redoublements() throws SQLException {
        redoubler(1);
        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 1 redoublement : conservee

        redoubler(1);
        assertEquals("2", texte("SELECT nombre_redoublements FROM etudiants WHERE id = 1"));
        assertEquals("0", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 2 redoublements : perdue
    }

    @Test
    void un_eleve_non_affecte_n_est_pas_concerne() throws SQLException {
        redoubler(2);
        redoubler(2);

        assertEquals("2", texte("SELECT nombre_redoublements FROM etudiants WHERE id = 2"));
        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 2"));
    }

    @Test
    void le_seuil_configure_est_respecte() throws SQLException {
        subvention.mettreAJourConfiguration(50, 30, 3);

        redoubler(1);
        redoubler(1);

        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 2 < 3
    }

    @Test
    void la_perte_n_est_signalee_qu_une_seule_fois() {
        Etudiant eleve = EtudiantRepository.obtenirParId(1);
        eleve.setNombreRedoublements(2);

        assertTrue(subvention.appliquerPerteSiSeuilAtteint(eleve));
        assertFalse(eleve.isSubventionActive());
        assertFalse(subvention.appliquerPerteSiSeuilAtteint(eleve));   // deja perdue : rien a faire
    }

    @Test
    void une_fois_perdue_la_reduction_est_nulle() {
        redoubler(1);
        redoubler(1);

        Etudiant eleve = EtudiantRepository.obtenirParId(1);

        assertEquals(0.0, subvention.getPourcentageReduction(eleve));
        assertEquals(100000.0, subvention.calculerFraisReels(eleve, 100000), 0.001);
    }
}