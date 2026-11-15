package com.fasodev.gestionscolaire.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie la migration 4 (année scolaire sur paiements et frais) sur une base SQLite en mémoire
 * qui reproduit le schéma précédent, avec des données déjà saisies.
 */
class MigrationFinancesAnneeTest {

    private static final String ANNEE_ECOLE = "2026-2027";
    private static final String ANNEE_PAR_DEFAUT = "2030-2031";

    private Connection base(String anneeActive) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE configuration_ecole (id INTEGER PRIMARY KEY, annee_scolaire_active TEXT)");
            st.execute("CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "table_name TEXT, record_id INTEGER, operation TEXT)");
            st.execute("""
                CREATE TABLE paiements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    montant REAL NOT NULL,
                    date_paiement DATE DEFAULT CURRENT_DATE,
                    statut TEXT CHECK (statut IN ('pay\u00e9', 'partiellement', 'impay\u00e9')),
                    notes TEXT,
                    cree_par TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            st.execute("""
                CREATE TABLE frais_scolarite (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    classe_id INTEGER NOT NULL,
                    montant REAL NOT NULL,
                    description TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            // Trigger de synchronisation comme celui de DatabaseInitializer (simplifié).
            st.execute("CREATE TRIGGER trg_sync_paiements_update AFTER UPDATE ON paiements BEGIN "
                + "INSERT INTO sync_queue (table_name, record_id, operation) "
                + "VALUES ('paiements', NEW.id, 'UPDATE'); END");

            if (anneeActive == null) {
                st.execute("INSERT INTO configuration_ecole (id) VALUES (1)");
            } else {
                st.execute("INSERT INTO configuration_ecole (id, annee_scolaire_active) VALUES (1, '" + anneeActive + "')");
            }
            st.execute("INSERT INTO paiements (etudiant_id, montant, statut) VALUES (1, 20000, 'partiellement')");
            st.execute("INSERT INTO paiements (etudiant_id, montant, statut) VALUES (2, 50000, 'pay\u00e9')");
            st.execute("INSERT INTO frais_scolarite (classe_id, montant, description) VALUES (1, 100000, 'Scolarit\u00e9')");
        }
        return conn;
    }

    private void migrer(Connection conn) throws SQLException {
        SchemaMigrations.appliquer(conn, List.of(MigrationFinancesAnnee.migration(ANNEE_PAR_DEFAUT)));
    }

    private String texte(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void les_lignes_existantes_sont_rattachees_a_l_annee_active_de_l_ecole() throws SQLException {
        try (Connection conn = base(ANNEE_ECOLE)) {
            migrer(conn);

            assertEquals(4, SchemaMigrations.versionActuelle(conn));
            assertEquals("2", texte(conn, "SELECT COUNT(*) FROM paiements WHERE annee_scolaire = '" + ANNEE_ECOLE + "'"));
            assertEquals("1", texte(conn, "SELECT COUNT(*) FROM frais_scolarite WHERE annee_scolaire = '" + ANNEE_ECOLE + "'"));
            assertEquals("70000.0", texte(conn, "SELECT SUM(montant) FROM paiements"));
        }
    }

    @Test
    void sans_annee_active_l_annee_par_defaut_est_utilisee() throws SQLException {
        try (Connection conn = base(null)) {
            migrer(conn);

            assertEquals("2", texte(conn, "SELECT COUNT(*) FROM paiements WHERE annee_scolaire = '" + ANNEE_PAR_DEFAUT + "'"));
        }
    }

    @Test
    void l_annee_devient_obligatoire() throws SQLException {
        try (Connection conn = base(ANNEE_ECOLE); Statement st = conn.createStatement()) {
            migrer(conn);

            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO paiements (etudiant_id, montant) VALUES (3, 1000)"));
            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO frais_scolarite (classe_id, montant) VALUES (1, 1000)"));
            assertThrows(SQLException.class, () -> st.execute(
                "UPDATE paiements SET annee_scolaire = '' WHERE id = 1"));

            st.execute("INSERT INTO paiements (etudiant_id, montant, annee_scolaire) VALUES (3, 1000, '2027-2028')");
            assertEquals("3", texte(conn, "SELECT COUNT(*) FROM paiements"));
        }
    }

    @Test
    void les_lignes_modifiees_sont_remises_en_file_de_synchronisation() throws SQLException {
        try (Connection conn = base(ANNEE_ECOLE)) {
            migrer(conn);

            assertEquals("2", texte(conn,
                "SELECT COUNT(*) FROM sync_queue WHERE table_name = 'paiements' AND operation = 'UPDATE'"));
        }
    }

    @Test
    void relancer_la_migration_ne_change_rien() throws SQLException {
        try (Connection conn = base(ANNEE_ECOLE)) {
            migrer(conn);
            migrer(conn);

            assertEquals("2", texte(conn, "SELECT COUNT(*) FROM paiements"));
            assertEquals("2", texte(conn,
                "SELECT COUNT(*) FROM sync_queue WHERE table_name = 'paiements'"));
        }
    }
}