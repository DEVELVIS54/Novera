package com.fasodev.gestionscolaire.database;

import com.fasodev.gestionscolaire.utils.AnneeScolaire;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 4 : année scolaire sur les paiements et les frais de scolarité.
 *
 * <p>Sans elle, les paiements de l'an dernier s'additionneraient à ceux de la nouvelle année et
 * fausseraient le solde. La migration :
 * <ul>
 *   <li>ajoute la colonne {@code annee_scolaire} aux deux tables (sans reconstruire les tables) ;</li>
 *   <li>rattache les lignes existantes à l'année active de l'école (déjà fixée par la migration 2) ;</li>
 *   <li>rend l'année obligatoire par deux triggers par table (insertion et modification) : un oubli
 *       échoue clairement au lieu de ranger la donnée dans la mauvaise année ;</li>
 *   <li>laisse les triggers de synchronisation existants remettre les lignes modifiées en file
 *       d'envoi, pour que le serveur reçoive l'année.</li>
 * </ul>
 */
final class MigrationFinancesAnnee {

    static final int VERSION = 4;

    private static final String[] TABLES = {"paiements", "frais_scolarite"};

    private MigrationFinancesAnnee() {
    }

    /** @param anneeParDefaut utilisée seulement si l'école n'a pas encore d'année active enregistrée */
    static SchemaMigrations.Migration migration(String anneeParDefaut) {
        if (!AnneeScolaire.estValide(anneeParDefaut)) {
            throw new IllegalArgumentException("Année scolaire invalide : " + anneeParDefaut);
        }
        return new SchemaMigrations.Migration(
            VERSION,
            "Année scolaire sur les paiements et les frais de scolarité",
            conn -> appliquer(conn, anneeParDefaut));
    }

    private static void appliquer(Connection conn, String anneeParDefaut) throws SQLException {
        String annee = anneeDeReference(conn, anneeParDefaut);

        for (String table : TABLES) {
            if (!colonneExiste(conn, table, "annee_scolaire")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN annee_scolaire TEXT");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + table + " SET annee_scolaire = ? "
                        + "WHERE annee_scolaire IS NULL OR annee_scolaire = ''")) {
                ps.setString(1, annee);
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TRIGGER IF NOT EXISTS trg_" + table + "_annee_insert "
                    + "BEFORE INSERT ON " + table + " "
                    + "WHEN NEW.annee_scolaire IS NULL OR NEW.annee_scolaire = '' "
                    + "BEGIN SELECT RAISE(ABORT, 'annee_scolaire obligatoire'); END");
                st.execute("CREATE TRIGGER IF NOT EXISTS trg_" + table + "_annee_update "
                    + "BEFORE UPDATE OF annee_scolaire ON " + table + " "
                    + "WHEN NEW.annee_scolaire IS NULL OR NEW.annee_scolaire = '' "
                    + "BEGIN SELECT RAISE(ABORT, 'annee_scolaire obligatoire'); END");
            }
        }
    }

    /** Année active de l'école si elle est enregistrée et valide, sinon l'année par défaut. */
    private static String anneeDeReference(Connection conn, String anneeParDefaut) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT annee_scolaire_active FROM configuration_ecole WHERE id = 1")) {
            if (rs.next() && AnneeScolaire.estValide(rs.getString(1))) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            // Table ou colonne absente (base très ancienne ou de test) : on utilise l'année par défaut.
        }
        return anneeParDefaut;
    }

    private static boolean colonneExiste(Connection conn, String table, String colonne) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (colonne.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}