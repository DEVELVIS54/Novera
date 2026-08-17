package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Utilisateur;

import java.sql.*;

/**
 * Accès aux données de la table "utilisateurs" (SQLite locale).
 */
public class UtilisateurRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /**
     * Retourne le nombre total d'utilisateurs enregistrés.
     * Utile pour détecter le premier lancement (0 utilisateur = setup requis).
     */
    public int compterUtilisateurs() {
        String sql = "SELECT COUNT(*) FROM utilisateurs";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture utilisateurs : " + e.getMessage(), e);
        }
    }

    public Utilisateur findByNomUtilisateur(String nomUtilisateur) {
        String sql = "SELECT * FROM utilisateurs WHERE nom_utilisateur = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nomUtilisateur);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapper(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur recherche utilisateur : " + e.getMessage(), e);
        }
    }

    public Utilisateur save(Utilisateur u) {
        String sql = """
            INSERT INTO utilisateurs (nom_utilisateur, password_hash, role, actif)
            VALUES (?, ?, ?, 1)
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, u.getNomUtilisateur());
            stmt.setString(2, u.getPasswordHash());
            stmt.setString(3, u.getRole());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                u.setId(keys.getInt(1));
            }
            return u;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création utilisateur : " + e.getMessage(), e);
        }
    }

    private Utilisateur mapper(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();
        u.setId(rs.getInt("id"));
        u.setNomUtilisateur(rs.getString("nom_utilisateur"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setActif(rs.getInt("actif") == 1);
        return u;
    }
}
