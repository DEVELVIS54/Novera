package com.fasodev.gestionscolaire.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie, sur une vraie base SQLite en mémoire, que les triggers alimentent sync_queue. */
class SyncTriggersTest {

    private Connection base() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT)");
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
            for (String sql : DatabaseInitializer.sqlTriggersSync("classes")) {
                st.execute(sql);
            }
        }
        return conn;
    }

    private List<String> file(Connection conn) throws SQLException {
        List<String> ops = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT operation, table_name, record_id FROM sync_queue ORDER BY id")) {
            while (rs.next()) {
                ops.add(rs.getString(1) + ":" + rs.getString(2) + ":" + rs.getInt(3));
            }
        }
        return ops;
    }

    @Test
    void insert_update_delete_sont_enregistres_dans_l_ordre() throws SQLException {
        try (Connection conn = base(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom) VALUES ('6A')");
            st.execute("UPDATE classes SET nom = '6B' WHERE id = 1");
            st.execute("DELETE FROM classes WHERE id = 1");

            assertEquals(List.of("INSERT:classes:1", "UPDATE:classes:1", "DELETE:classes:1"), file(conn));
        }
    }

    @Test
    void les_nouvelles_operations_sont_en_attente() throws SQLException {
        try (Connection conn = base(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom) VALUES ('5A')");
            try (ResultSet rs = st.executeQuery("SELECT status, synced_at FROM sync_queue")) {
                assertTrue(rs.next());
                assertEquals("pending", rs.getString(1));
                assertEquals(null, rs.getString(2));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void creation_des_triggers_est_idempotente() throws SQLException {
        try (Connection conn = base(); Statement st = conn.createStatement()) {
            for (String sql : DatabaseInitializer.sqlTriggersSync("classes")) {
                st.execute(sql); // 2e exécution : ne doit pas échouer ni dupliquer
            }
            st.execute("INSERT INTO classes (nom) VALUES ('4A')");
            assertEquals(1, file(conn).size());
        }
    }

    @Test
    void les_tables_synchronisees_n_incluent_pas_les_comptes_utilisateurs() {
        assertFalse(DatabaseInitializer.TABLES_SYNCHRONISEES.contains("utilisateurs"));
        assertTrue(DatabaseInitializer.TABLES_SYNCHRONISEES.contains("paiements"));
    }
}
