package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Note;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NoteRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public Note findByEtudiantMatiereTypeTrimestre(int etudiantId, int matiereId, String type, int trimestre) {
        String sql = "SELECT * FROM notes WHERE etudiant_id = ? AND matiere_id = ? AND type = ? AND trimestre = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, etudiantId);
            stmt.setInt(2, matiereId);
            stmt.setString(3, type);
            stmt.setInt(4, trimestre);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? mapper(rs) : null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture note : " + e.getMessage(), e);
        }
    }

    public List<Note> findByEtudiantAndTrimestre(int etudiantId, int trimestre) {
        String sql = "SELECT * FROM notes WHERE etudiant_id = ? AND trimestre = ?";
        List<Note> notes = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, etudiantId);
            stmt.setInt(2, trimestre);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) notes.add(mapper(rs));

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture notes : " + e.getMessage(), e);
        }
        return notes;
    }

    public void upsert(Note note) {
        Note existante = findByEtudiantMatiereTypeTrimestre(
            note.getEtudiantId(), note.getMatiereId(), note.getType(), note.getTrimestre()
        );

        if (existante == null) {
            insert(note);
        } else {
            note.setId(existante.getId());
            update(note);
        }
    }

    private void insert(Note note) {
        String sql = """
            INSERT INTO notes (etudiant_id, matiere_id, type, valeur, trimestre, date_saisie, cree_par, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, note.getEtudiantId());
            stmt.setInt(2, note.getMatiereId());
            stmt.setString(3, note.getType());
            stmt.setDouble(4, note.getValeur());
            stmt.setInt(5, note.getTrimestre());
            stmt.setString(6, LocalDate.now().toString());
            stmt.setString(7, note.getCreePar());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) note.setId(keys.getInt(1));

        } catch (SQLException e) {
            throw new RuntimeException("Erreur création note : " + e.getMessage(), e);
        }
    }

    private void update(Note note) {
        String sql = "UPDATE notes SET valeur = ?, cree_par = ?, sync_status = 'pending' WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, note.getValeur());
            stmt.setString(2, note.getCreePar());
            stmt.setInt(3, note.getId());
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour note : " + e.getMessage(), e);
        }
    }

    public void supprimer(int etudiantId, int matiereId, String type, int trimestre) {
        String sql = "DELETE FROM notes WHERE etudiant_id = ? AND matiere_id = ? AND type = ? AND trimestre = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, etudiantId);
            stmt.setInt(2, matiereId);
            stmt.setString(3, type);
            stmt.setInt(4, trimestre);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression note : " + e.getMessage(), e);
        }
    }

    private Note mapper(ResultSet rs) throws SQLException {
        Note n = new Note();
        n.setId(rs.getInt("id"));
        n.setEtudiantId(rs.getInt("etudiant_id"));
        n.setMatiereId(rs.getInt("matiere_id"));
        n.setType(rs.getString("type"));
        n.setValeur(rs.getDouble("valeur"));
        n.setTrimestre(rs.getInt("trimestre"));
        String date = rs.getString("date_saisie");
        if (date != null) n.setDateSaisie(LocalDate.parse(date));
        n.setCreePar(rs.getString("cree_par"));
        return n;
    }
}
