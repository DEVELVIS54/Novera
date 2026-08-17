package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Paiement;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PaiementRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<Paiement> findByEtudiantId(int etudiantId) {
        String sql = "SELECT * FROM paiements WHERE etudiant_id = ? ORDER BY date_paiement DESC";
        List<Paiement> liste = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, etudiantId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) liste.add(mapper(rs));

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture paiements : " + e.getMessage(), e);
        }
        return liste;
    }

    public double getTotalPayeParEtudiant(int etudiantId) {
        String sql = "SELECT COALESCE(SUM(montant), 0) FROM paiements WHERE etudiant_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, etudiantId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur calcul total payé : " + e.getMessage(), e);
        }
    }

    public Paiement save(Paiement p) {
        String sql = """
            INSERT INTO paiements (etudiant_id, montant, date_paiement, statut, notes, cree_par, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, p.getEtudiantId());
            stmt.setDouble(2, p.getMontant());
            stmt.setString(3, (p.getDatePaiement() != null ? p.getDatePaiement() : LocalDate.now()).toString());
            stmt.setString(4, p.getStatut());
            stmt.setString(5, p.getNotes());
            stmt.setString(6, p.getCreePar());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) p.setId(keys.getInt(1));
            return p;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur enregistrement paiement : " + e.getMessage(), e);
        }
    }

    public void delete(int id) {
        String sql = "DELETE FROM paiements WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression paiement : " + e.getMessage(), e);
        }
    }

    private Paiement mapper(ResultSet rs) throws SQLException {
        Paiement p = new Paiement();
        p.setId(rs.getInt("id"));
        p.setEtudiantId(rs.getInt("etudiant_id"));
        p.setMontant(rs.getDouble("montant"));
        String date = rs.getString("date_paiement");
        if (date != null) p.setDatePaiement(LocalDate.parse(date));
        p.setStatut(rs.getString("statut"));
        p.setNotes(rs.getString("notes"));
        p.setCreePar(rs.getString("cree_par"));
        return p;
    }
}
