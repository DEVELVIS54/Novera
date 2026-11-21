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