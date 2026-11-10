package com.fasodev.gestionscolaire.database;

import com.fasodev.gestionscolaire.database.SchemaMigrations.Migration;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie le versionnement du schéma sur une vraie base SQLite en mémoire. */
class SchemaMigrationsTest {

    private Connection baseVide() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    private boolean tableExiste(Connection conn, String nom) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + nom + "'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    private int nombreDeLignes(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Migration de test : crée une table et note son numéro dans la liste d'appels. */
    private Migration creeTable(int version, String table, List<Integer> appels) {
        return new Migration(version, "Création de " + table, conn -> {
            appels.add(version);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)");
            }
        });
    }

    @Test
    void une_base_neuve_ou_ancienne_est_marquee_version_de_base() throws SQLException {
        try (Connection conn = baseVide()) {
            SchemaMigrations.appliquer(conn, List.of());

            assertEquals(SchemaMigrations.VERSION_BASE, SchemaMigrations.versionActuelle(conn));
            assertEquals(1, nombreDeLignes(conn));
        }
    }

    @Test
    void les_migrations_s_appliquent_dans_l_ordre_et_une_seule_fois() throws SQLException {
        try (Connection conn = baseVide()) {
            List<Integer> appels = new ArrayList<>();
            List<Migration> migrations = List.of(
                creeTable(2, "table_deux", appels),
                creeTable(3, "table_trois", appels));

            SchemaMigrations.appliquer(conn, migrations);
            SchemaMigrations.appliquer(conn, migrations);   // 2e démarrage : rien à refaire

            assertEquals(List.of(2, 3), appels);
            assertEquals(3, SchemaMigrations.versionActuelle(conn));
            assertEquals(3, nombreDeLignes(conn));          // base + 2 migrations
            assertTrue(tableExiste(conn, "table_deux"));
            assertTrue(tableExiste(conn, "table_trois"));
        }
    }

    @Test
    void une_migration_ajoutee_plus_tard_n_applique_que_la_nouvelle() throws SQLException {
        try (Connection conn = baseVide()) {
            List<Integer> appels = new ArrayList<>();
            Migration deux = creeTable(2, "table_deux", appels);

            SchemaMigrations.appliquer(conn, List.of(deux));
            SchemaMigrations.appliquer(conn, List.of(deux, creeTable(3, "table_trois", appels)));

            assertEquals(List.of(2, 3), appels);
            assertEquals(3, SchemaMigrations.versionActuelle(conn));
        }
    }

    @Test
    void une_migration_en_echec_est_annulee_et_bloque_les_suivantes() throws SQLException {
        try (Connection conn = baseVide()) {
            List<Integer> appels = new ArrayList<>();
            Migration echec = new Migration(3, "Migration cassée", c -> {
                appels.add(3);
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE table_trois (id INTEGER PRIMARY KEY)");
                }
                throw new SQLException("boum");
            });
            List<Migration> migrations = List.of(
                creeTable(2, "table_deux", appels),
                echec,
                creeTable(4, "table_quatre", appels));

            assertThrows(SQLException.class, () -> SchemaMigrations.appliquer(conn, migrations));

            assertEquals(List.of(2, 3), appels);                  // la 4 n'a pas été tentée
            assertEquals(2, SchemaMigrations.versionActuelle(conn));
            assertTrue(tableExiste(conn, "table_deux"));          // la 2 est conservée
            assertFalse(tableExiste(conn, "table_trois"));        // la 3 est annulée
            assertFalse(tableExiste(conn, "table_quatre"));
        }
    }

    @Test
    void une_base_plus_recente_que_l_application_est_refusee() throws SQLException {
        try (Connection conn = baseVide()) {
            SchemaMigrations.appliquer(conn, List.of());
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO schema_version (version, description) VALUES (99, 'futur')");
            }

            assertThrows(IllegalStateException.class,
                () -> SchemaMigrations.appliquer(conn, List.of()));
        }
    }

    @Test
    void une_numerotation_incorrecte_est_refusee_avant_toute_modification() throws SQLException {
        try (Connection conn = baseVide()) {
            List<Integer> appels = new ArrayList<>();

            assertThrows(IllegalArgumentException.class, () -> SchemaMigrations.appliquer(conn,
                List.of(creeTable(3, "a", appels), creeTable(2, "b", appels))));
            assertThrows(IllegalArgumentException.class, () -> SchemaMigrations.appliquer(conn,
                List.of(creeTable(SchemaMigrations.VERSION_BASE, "c", appels))));

            assertTrue(appels.isEmpty());
            assertFalse(tableExiste(conn, "schema_version"));
        }
    }

    @Test
    void l_etat_des_cles_etrangeres_est_retabli_apres_une_migration() throws SQLException {
        try (Connection conn = baseVide()) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }

            SchemaMigrations.appliquer(conn, List.of(creeTable(2, "table_deux", new ArrayList<>())));

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void les_migrations_officielles_sont_bien_numerotees() {
        // Ne doit pas lever d'exception : garde-fou pour les futures migrations ajoutées.
        SchemaMigrations.verifierNumerotation(SchemaMigrations.MIGRATIONS);
    }
}