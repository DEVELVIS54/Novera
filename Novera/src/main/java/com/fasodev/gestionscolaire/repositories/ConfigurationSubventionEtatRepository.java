package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConfigurationSubventionEtatRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public static class Config {
        public double reductionPalierCep;
        public double reductionPalierBepc;
        public int seuilRedoublementsPerte;
    }

    public Config getConfiguration() {
        String sql = "SELECT * FROM configuration_subvention_etat WHERE id = 1";

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Config config = new Config();
            if (rs.next()) {
                config.reductionPalierCep = rs.getDouble("reduction_palier_cep");
                config.reductionPalierBepc = rs.getDouble("reduction_palier_bepc");
                config.seuilRedoublementsPerte = rs.getInt("seuil_redoublements_perte");
            }
            return config;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture configuration subvention : " + e.getMessage(), e);
        }
    }

    public void mettreAJour(double reductionCep, double reductionBepc, int seuilRedoublements) {
        String sql = """
            UPDATE configuration_subvention_etat
            SET reduction_palier_cep = ?, reduction_palier_bepc = ?, 
                seuil_redoublements_perte = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, reductionCep);
            stmt.setDouble(2, reductionBepc);
            stmt.setInt(3, seuilRedoublements);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour configuration subvention : " + e.getMessage(), e);
        }
    }
}
