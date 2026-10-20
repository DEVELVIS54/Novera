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

    public java.util.List<Utilisateur> findAll() {
        String sql = "SELECT * FROM utilisateurs ORDER BY role, nom_utilisateur";
        java.util.List<Utilisateur> liste = new java.util.ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                liste.add(mapper(rs));
            }
            return liste;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture utilisateurs : " + e.getMessage(), e);
        }
    }

    public Utilisateur findById(int id) {
        String sql = "SELECT * FROM utilisateurs WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? mapper(rs) : null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture utilisateur : " + e.getMessage(), e);
        }
    }

    public int compterAdminsActifs() {
        String sql = "SELECT COUNT(*) FROM utilisateurs WHERE role = 'ADMIN' AND actif = 1";

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture utilisateurs : " + e.getMessage(), e);
        }
    }

    public void changerActivation(int id, boolean actif) {
        String sql = "UPDATE utilisateurs SET actif = ? WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, actif ? 1 : 0);
            stmt.setInt(2, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur changement d'état utilisateur : " + e.getMessage(), e);
        }
    }

    public void changerMotDePasse(int id, String nouveauHash) {
        String sql = "UPDATE utilisateurs SET password_hash = ? WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nouveauHash);
            stmt.setInt(2, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur changement de mot de passe : " + e.getMessage(), e);
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
