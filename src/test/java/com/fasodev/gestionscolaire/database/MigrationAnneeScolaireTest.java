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
 * Vérifie la migration 2 (année scolaire) sur une base SQLite en mémoire qui reproduit
 * le schéma de la version 1, avec des données déjà saisies.
 */
class MigrationAnneeScolaireTest {

    private static final String ANNEE = "2026-2027";

    /** Base « version 1 » : les 3 tables concernées (sans année) + leurs tables liées, avec des données. */
    private Connection baseVersion1() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE configuration_ecole (id INTEGER PRIMARY KEY, nom TEXT)");
            st.execute("CREATE TABLE etudiants (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE matieres (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("""
                CREATE TABLE sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation TEXT CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
                    table_name TEXT NOT NULL,
                    record_id INTEGER NOT NULL,
                    payload TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    synced_at DATETIME,
                    status TEXT DEFAULT 'pending'
                )""");
            st.execute("""
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    matiere_id INTEGER NOT NULL,
                    type TEXT CHECK (type IN ('DEVOIR1', 'DEVOIR2', 'COMPOSITION', 'MOYENNE_DIRECTE')) NOT NULL,
                    valeur REAL,
                    trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                    date_saisie DATE DEFAULT CURRENT_DATE,
                    cree_par TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (matiere_id) REFERENCES matieres(id),
                    UNIQUE(etudiant_id, matiere_id, type, trimestre)
                )""");
            st.execute("""
                CREATE TABLE calcul_moyennes_trimestre (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    classe_id INTEGER NOT NULL,
                    trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                    statut TEXT DEFAULT 'non_calcule'
                        CHECK (statut IN ('non_calcule', 'calcule_verrouille')),
                    calcule_par TEXT,
                    date_calcul DATETIME,
                    date_deverrouillage DATETIME,
                    raison_deverrouillage TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (classe_id) REFERENCES classes(id),
                    UNIQUE(classe_id, trimestre)
                )""");
            st.execute("""
                CREATE TABLE moyennes_trimestrielles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    classe_id INTEGER NOT NULL,
                    trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                    moyenne REAL,
                    rang INTEGER,
                    calcul_id INTEGER NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (classe_id) REFERENCES classes(id),
                    FOREIGN KEY (calcul_id) REFERENCES calcul_moyennes_trimestre(id),
                    UNIQUE(etudiant_id, trimestre)
                )""");
            st.execute("CREATE INDEX idx_notes_etudiant ON notes(etudiant_id)");

            st.execute("PRAGMA foreign_keys = ON");
            st.execute("INSERT INTO configuration_ecole (id, nom) VALUES (1, 'Mon Établissement')");
            st.execute("INSERT INTO etudiants DEFAULT VALUES");
            st.execute("INSERT INTO classes DEFAULT VALUES");
            st.execute("INSERT INTO matieres DEFAULT VALUES");
            st.execute("INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre) "
                + "VALUES (1, 1, 'DEVOIR1', 12.5, 1)");
            st.execute("INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre, statut) "
                + "VALUES (1, 1, 'calcule_verrouille')");
            st.execute("INSERT INTO moyennes_trimestrielles (etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id) "
                + "VALUES (1, 1, 1, 14.0, 1, 1)");
        }
        return conn;
    }

    private void migrer(Connection conn) throws SQLException {
        SchemaMigrations.appliquer(conn, List.of(MigrationAnneeScolaire.migration(ANNEE)));
    }

    private String texte(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void les_donnees_existantes_sont_conservees_et_rattachees_a_l_annee() throws SQLException {
        try (Connection conn = baseVersion1()) {
            migrer(conn);

            assertEquals(2, SchemaMigrations.versionActuelle(conn));
            assertEquals("12.5", texte(conn, "SELECT valeur FROM notes WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM notes WHERE id = 1"));
            assertEquals("calcule_verrouille",
                texte(conn, "SELECT statut FROM calcul_moyennes_trimestre WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM calcul_moyennes_trimestre WHERE id = 1"));
            assertEquals("14.0", texte(conn, "SELECT moyenne FROM moyennes_trimestrielles WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM moyennes_trimestrielles WHERE id = 1"));
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire_active FROM configuration_ecole WHERE id = 1"));
        }
    }

    @Test
    void deux_annees_sont_possibles_mais_pas_un_doublon_dans_la_meme_annee() throws SQLException {
        try (Connection conn = baseVersion1(); Statement st = conn.createStatement()) {
            migrer(conn);

            // Année suivante : mêmes élève, matière, type et trimestre.
            st.execute("INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, annee_scolaire) "
                + "VALUES (1, 1, 'DEVOIR1', 9, 1, '2027-2028')");
            st.execute("INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre, annee_scolaire) "
                + "VALUES (1, 1, '2027-2028')");
            st.execute("INSERT INTO moyennes_trimestrielles "
                + "(etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id, annee_scolaire) "
                + "VALUES (1, 1, 1, 8.0, 1, 2, '2027-2028')");

            assertEquals("2", texte(conn, "SELECT COUNT(*) FROM notes"));

            // Doublon dans la même année : refusé par chacune des trois contraintes.
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, annee_scolaire) "
                    + "VALUES (1, 1, 'DEVOIR1', 7, 1, '2027-2028')"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre, annee_scolaire) "
                    + "VALUES (1, 1, '2027-2028')"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO moyennes_trimestrielles "
                    + "(etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id, annee_scolaire) "
                    + "VALUES (1, 1, 1, 6.0, 2, 2, '2027-2028')"));
        }
    }

    @Test
    void l_integrite_les_index_et_la_valeur_par_defaut_sont_en_place() throws SQLException {
        try (Connection conn = baseVersion1(); Statement st = conn.createStatement()) {
            migrer(conn);

            try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                assertFalse(rs.next(), "des clés étrangères sont cassées après la migration");
            }
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_notes_etudiant'"));
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'idx_notes_matiere'"));

            // Du code qui ne renseigne pas encore l'année continue de fonctionner.
            st.execute("INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre) "
                + "VALUES (1, 1, 'DEVOIR2', 10, 1)");
            assertEquals(ANNEE, texte(conn, "SELECT annee_scolaire FROM notes WHERE type = 'DEVOIR2'"));
        }
    }

    @Test
    void les_lignes_sont_remises_en_file_de_synchronisation() throws SQLException {
        try (Connection conn = baseVersion1()) {
            migrer(conn);

            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sync_queue WHERE operation = 'UPDATE' AND table_name = 'notes' AND record_id = 1"));
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sync_queue WHERE operation = 'UPDATE' AND table_name = 'calcul_moyennes_trimestre'"));
            assertEquals("1", texte(conn,
                "SELECT COUNT(*) FROM sync_queue WHERE operation = 'UPDATE' AND table_name = 'moyennes_trimestrielles'"));
        }
    }

    @Test
    void relancer_la_migration_ne_change_rien() throws SQLException {
        try (Connection conn = baseVersion1()) {
            migrer(conn);
            migrer(conn);

            assertEquals("1", texte(conn, "SELECT COUNT(*) FROM notes"));
            assertEquals(2, SchemaMigrations.versionActuelle(conn));
            assertEquals("1", texte(conn, "SELECT COUNT(*) FROM sync_queue WHERE table_name = 'notes'"));
        }
    }

    @Test
    void une_annee_invalide_est_refusee() {
        assertThrows(IllegalArgumentException.class, () -> MigrationAnneeScolaire.migration("2026"));
        assertThrows(IllegalArgumentException.class, () -> MigrationAnneeScolaire.migration("2026-2028"));
    }
}