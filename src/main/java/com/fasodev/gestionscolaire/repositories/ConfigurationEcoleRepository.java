package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.utils.AnneeScolaire;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * AccÃ¨s Ã  la configuration de l'Ã©cole (table Ã  1 seule ligne, id=1).
 */
public class ConfigurationEcoleRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public static class ConfigEcole {
        public String nom;
        public String adresse;
        public String email;
        public String telephone;
        public double seuilAdmission;
        public double seuilRedoublement;
    }

    public ConfigEcole getConfiguration() {
        String sql = "SELECT * FROM configuration_ecole WHERE id = 1";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ConfigEcole config = new ConfigEcole();
            if (rs.next()) {
                config.nom = rs.getString("nom");
                config.adresse = rs.getString("adresse");
                config.email = rs.getString("email");
                config.telephone = rs.getString("telephone");
                config.seuilAdmission = rs.getDouble("seuil_admission");
                config.seuilRedoublement = rs.getDouble("seuil_redoublement");
            }
            return config;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture configuration Ã©cole : " + e.getMessage(), e);
        }
    }

    public static ConfigEcole obtenirConfiguration() {
        return new ConfigurationEcoleRepository().getConfiguration();
    }

    public static void mettreAJourSeuils(double seuilAdmission, double seuilRedoublement) {
        String sql = """
            UPDATE configuration_ecole
            SET seuil_admission = ?, seuil_redoublement = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
        """;
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, seuilAdmission);
            stmt.setDouble(2, seuilRedoublement);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise Ã  jour des seuils : " + e.getMessage(), e);
        }
    }

    public void mettreAJourNom(String nom) {
        String sql = "UPDATE configuration_ecole SET nom = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nom);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise Ã  jour configuration Ã©cole : " + e.getMessage(), e);
        }
    }

    // ===================================================
    // AnnÃ©e scolaire active
    // Les notes, calculs et moyennes sont lus et Ã©crits dans cette annÃ©e.
    // ===================================================

    /** AnnÃ©e scolaire active de l'Ã©cole ; l'annÃ©e en cours si aucune n'est enregistrÃ©e. */
    public static String obtenirAnneeActive() {
        String sql = "SELECT annee_scolaire_active FROM configuration_ecole WHERE id = 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String annee = rs.getString(1);
                if (AnneeScolaire.estValide(annee)) {
                    return annee;
                }
            }
            return AnneeScolaire.courante();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture de l'annÃ©e scolaire active : " + e.getMessage(), e);
        }
    }

    /** Change l'annÃ©e scolaire active (format AAAA-AAAA, annÃ©es consÃ©cutives). */
    public static void definirAnneeActive(String annee) {
        if (!AnneeScolaire.estValide(annee)) {
            throw new IllegalArgumentException("AnnÃ©e scolaire invalide : " + annee);
        }
        String sql = "UPDATE configuration_ecole SET annee_scolaire_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, annee);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur changement d'annÃ©e scolaire : " + e.getMessage(), e);
        }
    }

    /** AnnÃ©es ayant des donnÃ©es, plus l'annÃ©e active, de la plus rÃ©cente Ã  la plus ancienne. */
    public static List<String> anneesDisponibles() {
        String sql = """
            SELECT annee_scolaire FROM notes
            UNION SELECT annee_scolaire FROM calcul_moyennes_trimestre
            UNION SELECT annee_scolaire FROM moyennes_trimestrielles
            UNION SELECT annee_scolaire FROM resultats_annuels
        """;
        TreeSet<String> annees = new TreeSet<>(Comparator.reverseOrder());
        annees.add(obtenirAnneeActive());
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String annee = rs.getString(1);
                if (AnneeScolaire.estValide(annee)) {
                    annees.add(annee);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture des annÃ©es scolaires : " + e.getMessage(), e);
        }
        return new ArrayList<>(annees);
    }
}