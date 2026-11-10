package com.fasodev.gestionscolaire.database;

import com.fasodev.gestionscolaire.utils.AnneeScolaire;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Versionnement du schéma de la base locale.
 *
 * <p>La table {@code schema_version} garde la liste des migrations déjà appliquées.
 * Au démarrage, {@link #appliquer(Connection)} exécute, dans l'ordre et une seule fois,
 * les migrations de {@link #MIGRATIONS} dont le numéro dépasse la version de la base.
 *
 * <p><b>Version 1 = état de référence.</b> Elle regroupe le schéma créé par
 * {@link DatabaseInitializer} et ses migrations historiques (colonnes ajoutées,
 * reconstruction des tables étudiants et réinscriptions), qui restent idempotentes
 * et s'exécutent à chaque démarrage. Une base existante sans {@code schema_version}
 * est donc simplement marquée « version 1 ».
 *
 * <p><b>Pour changer le schéma à partir de maintenant :</b> ajouter une entrée à
 * {@link #MIGRATIONS} avec le numéro suivant (2, 3, ...) ; ne jamais modifier ni
 * renuméroter une migration déjà livrée. Chaque migration s'exécute dans une
 * transaction (annulée si elle échoue) avec les clés étrangères désactivées, ce qui
 * permet de reconstruire une table. Si la table change aussi côté synchronisation,
 * penser aux triggers de {@code DatabaseInitializer.creerTriggersSync}.
 */
public final class SchemaMigrations {

    /** Version de référence : le schéma tel que créé par DatabaseInitializer. */
    public static final int VERSION_BASE = 1;

    private static final String DESCRIPTION_BASE =
        "Schéma de base (inclut les migrations historiques)";

    /** Code d'une migration. Reçoit la connexion, déjà dans une transaction. */
    @FunctionalInterface
    public interface Action {
        void executer(Connection conn) throws SQLException;
    }

    /** Une migration numérotée. {@code version} doit être strictement croissante. */
    public record Migration(int version, String description, Action action) {}

    /**
     * Migrations à appliquer après la version de base, par numéro croissant.
     * La prochaine à ajouter sera la version 3.
     *
     * <p>Version 2 : année scolaire. Les données déjà présentes sont rattachées à
     * l'année scolaire en cours au moment de la migration.
     */
    static final List<Migration> MIGRATIONS = List.of(
        MigrationAnneeScolaire.migration(AnneeScolaire.courante())
    );

    private SchemaMigrations() {
    }

    /** Met la base à niveau avec les migrations officielles de l'application. */
    public static void appliquer(Connection conn) throws SQLException {
        appliquer(conn, MIGRATIONS);
    }

    /** Variante testable : la liste de migrations est fournie par l'appelant. */
    static void appliquer(Connection conn, List<Migration> migrations) throws SQLException {
        verifierNumerotation(migrations);
        creerTable(conn);

        int actuelle = versionActuelle(conn);
        if (actuelle == 0) {
            enregistrer(conn, VERSION_BASE, DESCRIPTION_BASE);
            actuelle = VERSION_BASE;
        }

        int connue = migrations.isEmpty()
            ? VERSION_BASE
            : migrations.get(migrations.size() - 1).version();
        if (actuelle > connue) {
            throw new IllegalStateException(
                "Cette base de données a été créée par une version plus récente de Novera "
                    + "(schéma " + actuelle + ", cette version connaît jusqu'au schéma " + connue
                    + "). Mettez Novera à jour avant de l'ouvrir.");
        }

        for (Migration m : migrations) {
            if (m.version() > actuelle) {
                executer(conn, m);
                actuelle = m.version();
            }
        }
    }

    /** Version la plus élevée enregistrée, ou 0 si la table est vide. */
    static int versionActuelle(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static void verifierNumerotation(List<Migration> migrations) {
        int precedente = VERSION_BASE;
        for (Migration m : migrations) {
            if (m.version() <= precedente) {
                throw new IllegalArgumentException(
                    "Migrations mal numérotées : la version " + m.version()
                        + " doit être strictement supérieure à " + precedente + ".");
            }
            precedente = m.version();
        }
    }

    private static void creerTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
        }
    }

    private static void enregistrer(Connection conn, int version, String description) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_version (version, description) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }

    private static boolean foreignKeysActives(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    /** Exécute une migration dans une transaction ; tout ou rien. */
    private static void executer(Connection conn, Migration m) throws SQLException {
        boolean fkActives = foreignKeysActives(conn);
        try (Statement st = conn.createStatement()) {
            // PRAGMA foreign_keys est sans effet à l'intérieur d'une transaction.
            st.execute("PRAGMA foreign_keys = OFF");
            conn.setAutoCommit(false);
            try {
                m.action().executer(conn);
                enregistrer(conn, m.version(), m.description());
                conn.commit();
                System.out.println("🔧 Migration " + m.version() + " appliquée : " + m.description());
            } catch (SQLException e) {
                conn.rollback();
                throw new SQLException("Migration " + m.version() + " (" + m.description()
                    + ") échouée : " + e.getMessage(), e);
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                st.execute("PRAGMA foreign_keys = " + (fkActives ? "ON" : "OFF"));
            }
        }
    }
}