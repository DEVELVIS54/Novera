package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Matiere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MatiereRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<Matiere> findAll() {
        String sql = """
            SELECT m.*, c.nom AS classe_nom
            FROM matieres m
            JOIN classes c ON c.id = m.classe_id
            ORDER BY c.nom, m.nom
        """;
        List<Matiere> matieres = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                matieres.add(mapper(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture matières : " + e.getMessage(), e);
        }
        return matieres;
    }

    public List<Matiere> findByClasseId(int classeId) {
        String sql = """
            SELECT m.*, c.nom AS classe_nom
            FROM matieres m
            JOIN classes c ON c.id = m.classe_id
            WHERE m.classe_id = ?
            ORDER BY m.nom
        """;
        List<Matiere> matieres = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, classeId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                matieres.add(mapper(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture matières : " + e.getMessage(), e);
        }
        return matieres;
    }

    public boolean existeNomDansClasse(String nom, int classeId, Integer excludeId) {
        String sql = excludeId == null
            ? "SELECT COUNT(*) FROM matieres WHERE nom = ? AND classe_id = ?"
            : "SELECT COUNT(*) FROM matieres WHERE nom = ? AND classe_id = ? AND id != ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nom);
            stmt.setInt(2, classeId);
            if (excludeId != null) {
                stmt.setInt(3, excludeId);
            }
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification nom matière : " + e.getMessage(), e);
        }
    }

    public Matiere save(Matiere m) {
        String sql = """
            INSERT INTO matieres (nom, nom_professeur, coefficient, bareme_min, bareme_max, classe_id, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, m.getNom());
            stmt.setString(2, m.getNomProfesseur());
            stmt.setDouble(3, m.getCoefficient());
            stmt.setDouble(4, m.getBaremeMin());
            stmt.setDouble(5, m.getBaremeMax());
            stmt.setInt(6, m.getClasseId());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                m.setId(keys.getInt(1));
            }
            return m;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création matière : " + e.getMessage(), e);
        }
    }

    public void update(Matiere m) {
        String sql = """
            UPDATE matieres
            SET nom = ?, nom_professeur = ?, coefficient = ?, bareme_min = ?, bareme_max = ?, 
                classe_id = ?, sync_status = 'pending'
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, m.getNom());
            stmt.setString(2, m.getNomProfesseur());
            stmt.setDouble(3, m.getCoefficient());
            stmt.setDouble(4, m.getBaremeMin());
            stmt.setDouble(5, m.getBaremeMax());
            stmt.setInt(6, m.getClasseId());
            stmt.setInt(7, m.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour matière : " + e.getMessage(), e);
        }
    }

    public boolean aDesNotes(int matiereId) {
        String sql = "SELECT COUNT(*) FROM notes WHERE matiere_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, matiereId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification notes : " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM matieres WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression matière : " + e.getMessage(), e);
        }
    }

    private Matiere mapper(ResultSet rs) throws SQLException {
        Matiere m = new Matiere();
        m.setId(rs.getInt("id"));
        m.setNom(rs.getString("nom"));
        m.setNomProfesseur(rs.getString("nom_professeur"));
        m.setCoefficient(rs.getDouble("coefficient"));
        m.setBaremeMin(rs.getDouble("bareme_min"));
        m.setBaremeMax(rs.getDouble("bareme_max"));
        m.setClasseId(rs.getInt("classe_id"));
        m.setClasseNom(rs.getString("classe_nom"));
        return m;
    }
}
