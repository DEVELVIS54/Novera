package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.MoyenneResultat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CalculMoyenneRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** Retourne "non_calcule", "calcule_verrouille", ou null si aucune ligne n'existe encore. */
    public String getStatut(int classeId, int trimestre) {
        String sql = "SELECT statut FROM calcul_moyennes_trimestre WHERE classe_id = ? AND trimestre = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, classeId);
            stmt.setInt(2, trimestre);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("statut") : null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture statut calcul : " + e.getMessage(), e);
        }
    }

    public int obtenirOuCreerCalculId(int classeId, int trimestre) {
        String select = "SELECT id FROM calcul_moyennes_trimestre WHERE classe_id = ? AND trimestre = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(select)) {

            stmt.setInt(1, classeId);
            stmt.setInt(2, trimestre);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture calcul : " + e.getMessage(), e);
        }

        String insert = "INSERT INTO calcul_moyennes_trimestre (classe_id, trimestre) VALUES (?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, classeId);
            stmt.setInt(2, trimestre);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création calcul : " + e.getMessage(), e);
        }
    }

    public void verrouiller(int calculId, String calculePar) {
        String sql = """
            UPDATE calcul_moyennes_trimestre
            SET statut = 'calcule_verrouille', calcule_par = ?, date_calcul = ?
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calculePar);
            stmt.setString(2, LocalDateTime.now().toString());
            stmt.setInt(3, calculId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur verrouillage : " + e.getMessage(), e);
        }
    }

    public void deverrouiller(int classeId, int trimestre, String raison) {
        String sql = """
            UPDATE calcul_moyennes_trimestre
            SET statut = 'non_calcule', date_deverrouillage = ?, raison_deverrouillage = ?
            WHERE classe_id = ? AND trimestre = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setString(2, raison);
            stmt.setInt(3, classeId);
            stmt.setInt(4, trimestre);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur déverrouillage : " + e.getMessage(), e);
        }
    }

    public void supprimerMoyennesExistantes(int calculId) {
        String sql = "DELETE FROM moyennes_trimestrielles WHERE calcul_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, calculId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression anciennes moyennes : " + e.getMessage(), e);
        }
    }

    public void sauvegarderMoyennes(int calculId, int classeId, int trimestre, List<MoyenneResultat> resultats) {
        String sql = """
            INSERT INTO moyennes_trimestrielles (etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (MoyenneResultat r : resultats) {
                stmt.setInt(1, r.getEtudiantId());
                stmt.setInt(2, classeId);
                stmt.setInt(3, trimestre);
                stmt.setDouble(4, r.getMoyenne());
                stmt.setInt(5, r.getRang());
                stmt.setInt(6, calculId);
                stmt.addBatch();
            }
            stmt.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur sauvegarde moyennes : " + e.getMessage(), e);
        }
    }

    public List<MoyenneResultat> getMoyennes(int classeId, int trimestre) {
        String sql = """
            SELECT mt.*, e.prenom, e.nom
            FROM moyennes_trimestrielles mt
            JOIN etudiants e ON e.id = mt.etudiant_id
            WHERE mt.classe_id = ? AND mt.trimestre = ?
            ORDER BY mt.rang
        """;
        List<MoyenneResultat> resultats = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, classeId);
            stmt.setInt(2, trimestre);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                MoyenneResultat r = new MoyenneResultat(
                    rs.getInt("etudiant_id"),
                    rs.getString("prenom") + " " + rs.getString("nom"),
                    rs.getDouble("moyenne")
                );
                r.setRang(rs.getInt("rang"));
                resultats.add(r);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture moyennes : " + e.getMessage(), e);
        }
        return resultats;
    }
}
