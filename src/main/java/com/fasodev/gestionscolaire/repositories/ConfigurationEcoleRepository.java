package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Accès à la configuration de l'école (table à 1 seule ligne, id=1).
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
            throw new RuntimeException("Erreur lecture configuration école : " + e.getMessage(), e);
        }
    }

    public void mettreAJourNom(String nom) {
        String sql = "UPDATE configuration_ecole SET nom = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nom);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour configuration école : " + e.getMessage(), e);
        }
    }
}
