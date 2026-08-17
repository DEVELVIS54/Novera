package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.AccesParent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccesParentRepository {

    /**
     * Crée un nouvel accès parent (identifiant + mdp hash)
     */
    public static void creer(AccesParent acces) {
        String sql = """
            INSERT INTO parents (etudiant_id, identifiant, password_hash, date_generation, actif)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, acces.getEtudiantId());
            pstmt.setString(2, acces.getIdentifiant());
            pstmt.setString(3, acces.getPasswordHash());
            pstmt.setObject(4, acces.getDateGeneration());
            pstmt.setBoolean(5, acces.isActif() ? 1 : 0);

            pstmt.executeUpdate();

            // Récupérer l'ID auto-généré
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    acces.setId(rs.getInt(1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la création de l'accès parent : " + e.getMessage());
        }
    }

    /**
     * Récupère un accès par son identifiant (ex: LSJK-6A-0042)
     */
    public static AccesParent obtenirParIdentifiant(String identifiant) {
        String sql = "SELECT * FROM parents WHERE identifiant = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, identifiant);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapperResultSet(rs);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Récupère tous les accès pour un étudiant
     */
    public static List<AccesParent> obtenirParEtudiant(int etudiantId) {
        List<AccesParent> resultats = new ArrayList<>();
        String sql = "SELECT * FROM parents WHERE etudiant_id = ? ORDER BY date_generation DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, etudiantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    resultats.add(mapperResultSet(rs));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultats;
    }

    /**
     * Récupère tous les accès actifs (pour impression en masse)
     */
    public static List<AccesParent> obtenirTousActifs() {
        List<AccesParent> resultats = new ArrayList<>();
        String sql = "SELECT * FROM parents WHERE actif = 1 ORDER BY date_generation DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                resultats.add(mapperResultSet(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultats;
    }

    /**
     * Met à jour l'accès parent (ex: date de première utilisation)
     */
    public static void mettreAJour(AccesParent acces) {
        String sql = """
            UPDATE parents
            SET identifiant = ?, password_hash = ?, date_utilisation_premiere = ?, actif = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, acces.getIdentifiant());
            pstmt.setString(2, acces.getPasswordHash());
            pstmt.setObject(3, acces.getDateUtilisationPremiere());
            pstmt.setBoolean(4, acces.isActif() ? 1 : 0);
            pstmt.setInt(5, acces.getId());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la mise à jour de l'accès parent : " + e.getMessage());
        }
    }

    /**
     * Désactive un accès parent
     */
    public static void desactiver(int accesParentId) {
        String sql = "UPDATE parents SET actif = 0 WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, accesParentId);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la désactivation : " + e.getMessage());
        }
    }

    /**
     * Supprime un accès parent
     */
    public static void supprimer(int id) {
        String sql = "DELETE FROM parents WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    private static AccesParent mapperResultSet(ResultSet rs) throws Exception {
        AccesParent acces = new AccesParent();
        acces.setId(rs.getInt("id"));
        acces.setEtudiantId(rs.getInt("etudiant_id"));
        acces.setIdentifiant(rs.getString("identifiant"));
        acces.setPasswordHash(rs.getString("password_hash"));
        acces.setDateGeneration(rs.getObject("date_generation", LocalDateTime.class));
        acces.setDateUtilisationPremiere(rs.getObject("date_utilisation_premiere", LocalDateTime.class));
        acces.setActif(rs.getInt("actif") == 1);
        return acces;
    }
}
