package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.FraisScolarite;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FraisScolariteRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<FraisScolarite> findAll() {
        String sql = """
            SELECT f.*, c.nom AS classe_nom
            FROM frais_scolarite f
            JOIN classes c ON c.id = f.classe_id
            ORDER BY c.nom
        """;
        List<FraisScolarite> liste = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) liste.add(mapper(rs));

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture frais scolarité : " + e.getMessage(), e);
        }
        return liste;
    }

    /**
     * Retourne le montant total des frais pour une classe
     * (somme de toutes les lignes de frais rattachées, ex: scolarité + cotisation).
     */
    public double getMontantTotalParClasse(int classeId) {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM frais_scolarite WHERE classe_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, classeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur calcul frais classe : " + e.getMessage(), e);
        }
    }

    public FraisScolarite save(FraisScolarite f) {
        String sql = """
            INSERT INTO frais_scolarite (classe_id, montant, description, sync_status)
            VALUES (?, ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, f.getClasseId());
            stmt.setDouble(2, f.getMontant());
            stmt.setString(3, f.getDescription());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) f.setId(keys.getInt(1));
            return f;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création frais scolarité : " + e.getMessage(), e);
        }
    }

    public void update(FraisScolarite f) {
        String sql = """
            UPDATE frais_scolarite
            SET classe_id = ?, montant = ?, description = ?, sync_status = 'pending'
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, f.getClasseId());
            stmt.setDouble(2, f.getMontant());
            stmt.setString(3, f.getDescription());
            stmt.setInt(4, f.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour frais scolarité : " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM frais_scolarite WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression frais scolarité : " + e.getMessage(), e);
        }
    }

    private FraisScolarite mapper(ResultSet rs) throws SQLException {
        FraisScolarite f = new FraisScolarite();
        f.setId(rs.getInt("id"));
        f.setClasseId(rs.getInt("classe_id"));
        f.setClasseNom(rs.getString("classe_nom"));
        f.setMontant(rs.getDouble("montant"));
        f.setDescription(rs.getString("description"));
        return f;
    }
}
