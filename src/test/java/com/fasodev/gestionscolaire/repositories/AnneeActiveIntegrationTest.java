package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.models.Note;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intÃ©gration : une vraie base SQLite (fichier temporaire) crÃ©Ã©e par
 * DatabaseInitializer, avec ses migrations, puis utilisÃ©e par les repositories.
 * La vraie base de l'utilisateur n'est jamais touchÃ©e.
 */
class AnneeActiveIntegrationTest {

    @TempDir
    Path dossier;

    private NoteRepository notes;
    private CalculMoyenneRepository calculs;
    private String anneeA;   // annÃ©e active au dÃ©part
    private String anneeB;   // annÃ©e suivante

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('6eme A', '6eme')");
            st.execute("INSERT INTO matieres (nom, classe_id) VALUES ('Maths', 1)");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Awa', 'Traore', 1)");
        }

        notes = new NoteRepository();
        calculs = new CalculMoyenneRepository();
        anneeA = ConfigurationEcoleRepository.obtenirAnneeActive();
        anneeB = AnneeScolaire.suivante(anneeA);
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private int compter(String sql) throws SQLException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private MoyenneResultat resultat(double moyenne) {
        MoyenneResultat r = new MoyenneResultat(1, "Awa Traore", moyenne);
        r.setRang(1);
        return r;
    }

    @Test
    void l_annee_active_par_defaut_est_l_annee_en_cours() {
        assertEquals(AnneeScolaire.courante(), anneeA);
    }

    @Test
    void une_note_est_enregistree_dans_l_annee_active() throws SQLException {
        notes.upsert(new Note(1, 1, Note.TYPE_DEVOIR1, 12.5, 1));

        assertEquals(1, compter("SELECT COUNT(*) FROM notes WHERE annee_scolaire = '" + anneeA + "'"));
    }

    @Test
    void changer_d_annee_isole_les_notes() throws SQLException {
        notes.upsert(new Note(1, 1, Note.TYPE_DEVOIR1, 12.5, 1));

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertNull(notes.findByEtudiantMatiereTypeTrimestre(1, 1, Note.TYPE_DEVOIR1, 1));
        assertTrue(notes.findByEtudiantAndTrimestre(1, 1).isEmpty());

        // MÃªmes Ã©lÃ¨ve, matiÃ¨re, type et trimestre : une 2e note, pas un Ã©crasement.
        notes.upsert(new Note(1, 1, Note.TYPE_DEVOIR1, 9.0, 1));
        assertEquals(2, compter("SELECT COUNT(*) FROM notes"));
        assertEquals(9.0, notes.findByEtudiantMatiereTypeTrimestre(1, 1, Note.TYPE_DEVOIR1, 1).getValeur());

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals(12.5, notes.findByEtudiantMatiereTypeTrimestre(1, 1, Note.TYPE_DEVOIR1, 1).getValeur());
        assertEquals(1, notes.findByEtudiantAndTrimestre(1, 1).size());
    }

    @Test
    void supprimer_ne_touche_que_l_annee_active() throws SQLException {
        notes.upsert(new Note(1, 1, Note.TYPE_MOYENNE_DIRECTE, 14.0, 1));
        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        notes.upsert(new Note(1, 1, Note.TYPE_MOYENNE_DIRECTE, 8.0, 1));

        notes.supprimer(1, 1, Note.TYPE_MOYENNE_DIRECTE, 1);

        assertEquals(1, compter("SELECT COUNT(*) FROM notes"));
        assertEquals(1, compter("SELECT COUNT(*) FROM notes WHERE annee_scolaire = '" + anneeA + "'"));
    }

    @Test
    void les_calculs_et_les_moyennes_sont_separes_par_annee() {
        int calculA = calculs.obtenirOuCreerCalculId(1, 1);
        calculs.verrouiller(calculA, "directeur");
        calculs.sauvegarderMoyennes(calculA, 1, 1, List.of(resultat(14.0)));
        assertEquals("calcule_verrouille", calculs.getStatut(1, 1));

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertNull(calculs.getStatut(1, 1));
        assertTrue(calculs.getMoyennes(1, 1).isEmpty());
        assertEquals(0.0, calculs.getMoyenneEtudiant(1, 1));

        int calculB = calculs.obtenirOuCreerCalculId(1, 1);
        assertNotEquals(calculA, calculB);
        calculs.sauvegarderMoyennes(calculB, 1, 1, List.of(resultat(8.0)));
        assertEquals(8.0, calculs.getMoyenneEtudiant(1, 1));
        assertEquals(1, calculs.getMoyennes(1, 1).size());

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals(14.0, calculs.getMoyenneEtudiant(1, 1));
        assertEquals("calcule_verrouille", calculs.getStatut(1, 1));
    }

    @Test
    void deverrouiller_ne_concerne_que_l_annee_active() {
        int calculA = calculs.obtenirOuCreerCalculId(1, 1);
        calculs.verrouiller(calculA, "directeur");

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        int calculB = calculs.obtenirOuCreerCalculId(1, 1);
        calculs.verrouiller(calculB, "directeur");
        calculs.deverrouiller(1, 1, "correction");
        assertEquals("non_calcule", calculs.getStatut(1, 1));

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals("calcule_verrouille", calculs.getStatut(1, 1));
    }

    @Test
    void l_annee_active_se_change_et_se_valide() {
        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertEquals(anneeB, ConfigurationEcoleRepository.obtenirAnneeActive());

        assertThrows(IllegalArgumentException.class,
            () -> ConfigurationEcoleRepository.definirAnneeActive("2026"));
        assertEquals(anneeB, ConfigurationEcoleRepository.obtenirAnneeActive());
    }

    @Test
    void les_annees_disponibles_incluent_l_annee_active_et_celles_qui_ont_des_donnees() {
        notes.upsert(new Note(1, 1, Note.TYPE_DEVOIR1, 12.0, 1));
        ConfigurationEcoleRepository.definirAnneeActive(anneeB);

        assertEquals(List.of(anneeB, anneeA), ConfigurationEcoleRepository.anneesDisponibles());
    }
}