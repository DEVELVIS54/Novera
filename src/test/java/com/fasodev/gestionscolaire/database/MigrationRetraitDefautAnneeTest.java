package com.fasodev.gestionscolaire.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VÃ©rifie la migration 3 (annÃ©e scolaire obligatoire) sur une base SQLite en mÃ©moire qui
 * reproduit le schÃ©ma de la version 2 (annÃ©e avec valeur par dÃ©faut), avec des donnÃ©es.
 */
class MigrationRetraitDefautAnneeTest {

    private static final String ANNEE = "2026-2027";

    private Connection baseVersion2() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE etudiants (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE matieres (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute(MigrationAnneeScolaire.ddlNotes("notes", ANNEE));
            st.execute(MigrationAnneeScolaire.ddlCalculs("calcul_moyennes_trimestre", ANNEE));
            st.execute(MigrationAnneeScolaire.ddlMoyennes("moyennes_trimestrielles", ANNEE));
            st.execute("CREATE INDEX idx_notes_etudiant ON notes(etudiant_id)");

            st.execute("PRAGMA foreign_keys = ON");
            st.execute("INSERT INTO etudiants DEFAULT VALUES");
            st.execute("INSERT INTO classes DEFAULT VALUES");
            st.execute("INSERT INTO matieres DEFAULT VALUES");
            st.execute("INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, annee_scolaire) "
                + "VALUES (1, 1, 'DEVOIR1', 12.5, 1, '" + ANNEE + "')");
            st.execute("INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre, statut, annee_scolaire) "
                + "VALUES (1, 1, 'calcule_verrouille', '" + ANNEE + "')");
            st.execute("INSERT INTO moyennes_trimestrielles "
                + "(etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id, annee_scolaire) "
                + "VALUES (1, 1, 1, 14.0, 1, 1, '" + ANNEE + "')");
        }
        return conn;
    }

    private void migrer(Connection conn) throws SQLException {
        SchemaMigrations.appliquer(conn, List.of(MigrationRetraitDefautAnnee.migration()));
    }

    private String texte(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void les_donnees_et_leur_annee_sont_conservees() throws SQLException {
        try (Connection conn = baseVersion2()) {
            migrer(conn);

            assertEquals(3, SchemaMigrations.versionActuelle(conn));
            assertEquals("12.5", texte(conn, "SELECT valeur FROM notes WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM notes WHERE id = 1"));
            assertEquals("calcule_verrouille",
                texte(conn, "SELECT statut FROM calcul_moyennes_trimestre WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM calcul_moyennes_trimestre WHERE id = 1"));
            assertEquals("14.0", texte(conn, "SELECT moyenne FROM moyennes_trimestrielles WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM moyennes_trimestrielles WHERE id = 1"));
        }
    }

    @Test
    void l_annee_devient_obligatoire() throws SQLException {
        try (Connection conn = baseVersion2(); Statement st = conn.createStatement()) {
            migrer(conn);

            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre) "
                    + "VALUES (1, 1, 'DEVOIR2', 10, 1)"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre) VALUES (1, 2)"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO moyennes_trimestrielles (etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id) "
                    + "VALUES (1, 1, 2, 9.0, 1, 1)"));
        }
    }

    @Test
    void deux_annees_restent_possibles_mais_pas_un_doublon_dans_la_meme_annee() throws SQLException {
        try (Connection conn = baseVersion2(); Statement st = conn.createStatement()) {
            migrer(conn);

            st.execute("INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, annee_scolaire) "
                + "VALUES (1, 1, 'DEVOIR1', 9, 1, '2027-2028')");
            assertEquals("2", texte(conn, "SELECT COUNT(*) FROM notes"));

            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, annee_scolaire) "
                    + "VALUES (1, 1, 'DEVOIR1', 7, 1, '2027-2028')"));
        }
    }

    @Test
    void l_integrite_les_index_et_la_suppression_du_defaut_sont_verifies() throws SQLException {
        try (Connection conn = baseVersion2(); Statement st = conn.createStatement()) {
            migrer(conn);

            try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(rs.next(), "des clÃ©s Ã©trangÃ¨res sont cassÃ©es aprÃ¨s la migration");
            }
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_notes_etudiant'"));
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_notes_matiere'"));
            assertFalse(texte(conn, "SELECT sql FROM sqlite_master WHERE name = 'notes'").contains("DEFAULT '" + ANNEE));
        }
    }

    @Test
    void relancer_la_migration_ne_change_rien() throws SQLException {
        try (Connection conn = baseVersion2()) {
            migrer(conn);
            migrer(conn);

            assertEquals("1", texte(conn, "SELECT COUNT(*) FROM notes"));
            assertEquals(3, SchemaMigrations.versionActuelle(conn));
        }
    }
}