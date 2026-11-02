package com.fasodev.gestionscolaire.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Vérifie, sur une vraie base SQLite en mémoire, que la migration de la table
 * reinscriptions accepte le statut 'refusee' sans perdre les données existantes.
 */
class ReinscriptionMigrationTest {

    /** Définition de la table telle qu'elle existait avant la correction. */
    private static final String ANCIEN_DDL = """
        CREATE TABLE reinscriptions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            etudiant_id INTEGER NOT NULL,
            resultat_examen_id INTEGER NOT NULL,
            nouvelle_classe_id INTEGER,
            statut TEXT DEFAULT 'en_attente' CHECK (statut IN ('en_attente','confirmee')),
            confirmee_par TEXT,
            date_confirmation DATETIME,
            annee_scolaire TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
            FOREIGN KEY (resultat_examen_id) REFERENCES resultats_examen_national(id),
            FOREIGN KEY (nouvelle_classe_id) REFERENCES classes(id)
        )""";

    /** Base « ancienne version » : tables liées minimales + 2 réinscriptions existantes. */
    private Connection ancienneBase() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE etudiants (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("CREATE TABLE resultats_examen_national (id INTEGER PRIMARY KEY AUTOINCREMENT)");
            st.execute("INSERT INTO etudiants DEFAULT VALUES");
            st.execute("INSERT INTO etudiants DEFAULT VALUES");
            st.execute("INSERT INTO etudiants DEFAULT VALUES");
            st.execute("INSERT INTO resultats_examen_national DEFAULT VALUES");
            st.execute(ANCIEN_DDL);
            st.execute("INSERT INTO reinscriptions (etudiant_id, resultat_examen_id, annee_scolaire, statut) "
                + "VALUES (1, 1, '2025-2026', 'confirmee'), (2, 1, '2025-2026', 'en_attente')");
        }
        return conn;
    }

    private int compter(Connection conn, String where) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM reinscriptions WHERE " + where)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void l_ancienne_table_refuse_le_statut_refusee() throws SQLException {
        try (Connection conn = ancienneBase(); Statement st = conn.createStatement()) {
            assertThrows(SQLException.class,
                () -> st.execute("UPDATE reinscriptions SET statut = 'refusee' WHERE id = 2"));
        }
    }

    @Test
    void la_migration_accepte_refusee_et_conserve_les_donnees() throws SQLException {
        try (Connection conn = ancienneBase()) {
            DatabaseInitializer.migrerTableReinscriptions(conn);

            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE reinscriptions SET statut = 'refusee' WHERE id = 2");
            }

            assertEquals(2, compter(conn, "1 = 1"));
            assertEquals(1, compter(conn, "id = 1 AND statut = 'confirmee' AND annee_scolaire = '2025-2026'"));
            assertEquals(1, compter(conn, "id = 2 AND statut = 'refusee'"));
        }
    }

    @Test
    void la_migration_est_idempotente() throws SQLException {
        try (Connection conn = ancienneBase()) {
            DatabaseInitializer.migrerTableReinscriptions(conn);
            DatabaseInitializer.migrerTableReinscriptions(conn);

            assertEquals(2, compter(conn, "1 = 1"));
        }
    }

    @Test
    void les_identifiants_continuent_apres_la_migration() throws SQLException {
        try (Connection conn = ancienneBase()) {
            DatabaseInitializer.migrerTableReinscriptions(conn);

            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO reinscriptions (etudiant_id, resultat_examen_id, annee_scolaire) "
                    + "VALUES (3, 1, '2025-2026')");
            }

            assertEquals(1, compter(conn, "id = 3 AND statut = 'en_attente'"));
        }
    }

    @Test
    void une_nouvelle_base_accepte_refusee_mais_pas_n_importe_quel_statut() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = conn.createStatement()) {
            st.execute(DatabaseInitializer.ddlReinscriptions("reinscriptions", true));

            st.execute("INSERT INTO reinscriptions (etudiant_id, resultat_examen_id, annee_scolaire, statut) "
                + "VALUES (1, 1, '2025-2026', 'refusee')");
            assertEquals(1, compter(conn, "statut = 'refusee'"));

            assertThrows(SQLException.class, () -> st.execute(
                "INSERT INTO reinscriptions (etudiant_id, resultat_examen_id, annee_scolaire, statut) "
                    + "VALUES (2, 1, '2025-2026', 'nimporte_quoi')"));
        }
    }
}