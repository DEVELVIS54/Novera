package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Etudiant;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EtudiantRepository {

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public List<Etudiant> findAll() {
        String sql = """
            SELECT e.*, c.nom AS classe_nom
            FROM etudiants e
            JOIN classes c ON c.id = e.classe_id
            ORDER BY e.nom, e.prenom
        """;
        return executerRequeteListe(sql, stmt -> {});
    }

    public List<Etudiant> findActifs() {
        String sql = """
            SELECT e.*, c.nom AS classe_nom
            FROM etudiants e
            JOIN classes c ON c.id = e.classe_id
            WHERE e.statut_scolarite = 'actif'
            ORDER BY e.nom, e.prenom
        """;
        return executerRequeteListe(sql, stmt -> {});
    }

    public List<Etudiant> findByClasseId(int classeId) {
        String sql = """
            SELECT e.*, c.nom AS classe_nom
            FROM etudiants e
            JOIN classes c ON c.id = e.classe_id
            WHERE e.classe_id = ? AND e.statut_scolarite = 'actif'
            ORDER BY e.nom, e.prenom
        """;
        return executerRequeteListe(sql, stmt -> stmt.setInt(1, classeId));
    }

    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    private List<Etudiant> executerRequeteListe(String sql, StatementBinder binder) {
        List<Etudiant> etudiants = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            binder.bind(stmt);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                etudiants.add(mapper(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture étudiants : " + e.getMessage(), e);
        }
        return etudiants;
    }

    public Etudiant findById(int id) {
        String sql = """
            SELECT e.*, c.nom AS classe_nom
            FROM etudiants e
            JOIN classes c ON c.id = e.classe_id
            WHERE e.id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? mapper(rs) : null;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture étudiant : " + e.getMessage(), e);
        }
    }

    public boolean existeMatricule(String matricule, Integer excludeId) {
        String sql = excludeId == null
            ? "SELECT COUNT(*) FROM etudiants WHERE matricule = ?"
            : "SELECT COUNT(*) FROM etudiants WHERE matricule = ? AND id != ?";

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, matricule);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification matricule : " + e.getMessage(), e);
        }
    }

    public Etudiant save(Etudiant e) {
        String sql = """
            INSERT INTO etudiants
                (prenom, nom, date_naissance, classe_id, matricule, statut_scolarite,
                 affecte_etat, palier_subvention, sync_status)
            VALUES (?, ?, ?, ?, ?, 'actif', ?, ?, 'pending')
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, e.getPrenom());
            stmt.setString(2, e.getNom());
            stmt.setString(3, e.getDateNaissance() != null ? e.getDateNaissance().toString() : null);
            stmt.setInt(4, e.getClasseId());
            stmt.setString(5, e.getMatricule());
            stmt.setInt(6, e.isAffecteEtat() ? 1 : 0);
            stmt.setString(7, e.getPalierSubvention());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                e.setId(keys.getInt(1));
            }

            // Si aucun matricule fourni, en générer un basé sur l'id
            if (e.getMatricule() == null || e.getMatricule().isBlank()) {
                String matriculeAuto = "ETU" + String.format("%05d", e.getId());
                mettreAJourMatricule(e.getId(), matriculeAuto);
                e.setMatricule(matriculeAuto);
            }

            return e;

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur création étudiant : " + ex.getMessage(), ex);
        }
    }

    private void mettreAJourMatricule(int id, String matricule) {
        String sql = "UPDATE etudiants SET matricule = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, matricule);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur génération matricule : " + e.getMessage(), e);
        }
    }

    public void update(Etudiant e) {
        String sql = """
            UPDATE etudiants
            SET prenom = ?, nom = ?, date_naissance = ?, classe_id = ?, matricule = ?,
                affecte_etat = ?, palier_subvention = ?, sync_status = 'pending'
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, e.getPrenom());
            stmt.setString(2, e.getNom());
            stmt.setString(3, e.getDateNaissance() != null ? e.getDateNaissance().toString() : null);
            stmt.setInt(4, e.getClasseId());
            stmt.setString(5, e.getMatricule());
            stmt.setInt(6, e.isAffecteEtat() ? 1 : 0);
            stmt.setString(7, e.getPalierSubvention());
            stmt.setInt(8, e.getId());
            stmt.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur mise à jour étudiant : " + ex.getMessage(), ex);
        }
    }

    /**
     * Marque un étudiant comme parti (départ définitif de l'établissement).
     * Ne supprime jamais la ligne — l'historique est conservé.
     */
    public void marquerCommeParti(int id, LocalDate dateDepart, String raison) {
        String sql = """
            UPDATE etudiants
            SET statut_scolarite = 'parti', date_depart = ?, raison_depart = ?, sync_status = 'pending'
            WHERE id = ?
        """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dateDepart.toString());
            stmt.setString(2, raison);
            stmt.setInt(3, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur marquage départ étudiant : " + e.getMessage(), e);
        }
    }

    private Etudiant mapper(ResultSet rs) throws SQLException {
        Etudiant e = new Etudiant();
        e.setId(rs.getInt("id"));
        e.setPrenom(rs.getString("prenom"));
        e.setNom(rs.getString("nom"));

        String dateNaissance = rs.getString("date_naissance");
        if (dateNaissance != null) {
            e.setDateNaissance(LocalDate.parse(dateNaissance));
        }

        e.setClasseId(rs.getInt("classe_id"));
        e.setClasseNom(rs.getString("classe_nom"));
        e.setMatricule(rs.getString("matricule"));
        e.setStatutScolarite(rs.getString("statut_scolarite"));

        String dateDepart = rs.getString("date_depart");
        if (dateDepart != null) {
            e.setDateDepart(LocalDate.parse(dateDepart));
        }

        e.setRaisonDepart(rs.getString("raison_depart"));
        e.setAffecteEtat(rs.getInt("affecte_etat") == 1);
        e.setPalierSubvention(rs.getString("palier_subvention"));
        e.setNombreRedoublements(rs.getInt("nombre_redoublements"));
        e.setSubventionActive(rs.getInt("subvention_active") == 1);

        return e;
    }
}
