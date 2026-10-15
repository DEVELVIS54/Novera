package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Classe;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClasseRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<Classe> findAll() {
        String sql = "SELECT * FROM classes ORDER BY nom";
        List<Classe> classes = new ArrayList<>();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                classes.add(mapper(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture classes : " + e.getMessage(), e);
        }
        return classes;
    }

    public Classe findById(int id) {
        String sql = "SELECT * FROM classes WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? mapper(rs) : null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture classe : " + e.getMessage(), e);
        }
    }

    public boolean existeNom(String nom, Integer excludeId) {
        String sql = excludeId == null
            ? "SELECT COUNT(*) FROM classes WHERE nom = ?"
            : "SELECT COUNT(*) FROM classes WHERE nom = ? AND id != ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nom);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification nom classe : " + e.getMessage(), e);
        }
    }

    public Classe save(Classe c) {
        String sql = """
            INSERT INTO classes (nom, niveau, est_classe_examen, nom_examen, fin_de_parcours, sync_status)
            VALUES (?, ?, ?, ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, c.getNom());
            stmt.setString(2, c.getNiveau());
            stmt.setInt(3, c.isEstClasseExamen() ? 1 : 0);
            stmt.setString(4, c.getNomExamen());
            stmt.setInt(5, c.isFinDeParcours() ? 1 : 0);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                c.setId(keys.getInt(1));
            }
            return c;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création classe : " + e.getMessage(), e);
        }
    }

    public void update(Classe c) {
        String sql = """
            UPDATE classes
            SET nom = ?, niveau = ?, est_classe_examen = ?, nom_examen = ?, 
                fin_de_parcours = ?, sync_status = 'pending'
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, c.getNom());
            stmt.setString(2, c.getNiveau());
            stmt.setInt(3, c.isEstClasseExamen() ? 1 : 0);
            stmt.setString(4, c.getNomExamen());
            stmt.setInt(5, c.isFinDeParcours() ? 1 : 0);
            stmt.setInt(6, c.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour classe : " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie si des étudiants sont rattachés à cette classe
     * (empêche une suppression qui casserait l'intégrité des données).
     */
    public boolean aDesEtudiants(int classeId) {
        String sql = "SELECT COUNT(*) FROM etudiants WHERE classe_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, classeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification étudiants : " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM classes WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression classe : " + e.getMessage(), e);
        }
    }

    private Classe mapper(ResultSet rs) throws SQLException {
        Classe c = new Classe();
        c.setId(rs.getInt("id"));
        c.setNom(rs.getString("nom"));
        c.setNiveau(rs.getString("niveau"));
        c.setEstClasseExamen(rs.getInt("est_classe_examen") == 1);
        c.setNomExamen(rs.getString("nom_examen"));
        c.setFinDeParcours(rs.getInt("fin_de_parcours") == 1);
        return c;
    }
}
