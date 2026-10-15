package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.ResultatExamenNational;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ResultatExamenNationalRepository {

    /**
     * Crée un nouveau résultat d'examen
     */
    public static void creer(ResultatExamenNational resultat) {
        String sql = """
            INSERT INTO resultats_examen_national 
            (etudiant_id, classe_id, annee_scolaire, nom_examen, resultat, decision_si_refuse, saisi_par)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, resultat.getEtudiantId());
            pstmt.setInt(2, resultat.getClasseId());
            pstmt.setString(3, resultat.getAnnee_scolaire());
            pstmt.setString(4, resultat.getNomExamen());
            pstmt.setString(5, resultat.getResultat());
            pstmt.setString(6, resultat.getDecision_si_refuse());
            pstmt.setString(7, resultat.getSaisi_par());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    resultat.setId(rs.getInt(1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur création résultat examen : " + e.getMessage());
        }
    }

    /**
     * Récupère un résultat par ID
     */
    public static ResultatExamenNational obtenirParId(int id) {
        String sql = "SELECT * FROM resultats_examen_national WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

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
     * Récupère tous les résultats pour une classe et année
     */
    public static List<ResultatExamenNational> obtenirParClasse(int classeId, String annee) {
        List<ResultatExamenNational> resultats = new ArrayList<>();
        String sql = """
            SELECT r.*, e.nom, e.prenom, c.nom as classe_nom
            FROM resultats_examen_national r
            JOIN etudiants e ON r.etudiant_id = e.id
            JOIN classes c ON r.classe_id = c.id
            WHERE r.classe_id = ? AND r.annee_scolaire = ?
            ORDER BY e.nom, e.prenom
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, classeId);
            pstmt.setString(2, annee);

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
     * Récupère les résultats non finalisés (ceux qui attendent décision Secrétaire)
     */
    public static List<ResultatExamenNational> obtenirNonFinalisés(String annee) {
        List<ResultatExamenNational> resultats = new ArrayList<>();
        String sql = """
            SELECT r.*, e.nom, e.prenom, c.nom as classe_nom
            FROM resultats_examen_national r
            JOIN etudiants e ON r.etudiant_id = e.id
            JOIN classes c ON r.classe_id = c.id
            WHERE r.statut_final IS NULL AND r.annee_scolaire = ?
            ORDER BY r.date_saisie DESC
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, annee);

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
     * Met à jour un résultat d'examen
     */
    public static void mettreAJour(ResultatExamenNational resultat) {
        String sql = """
            UPDATE resultats_examen_national
            SET resultat = ?, decision_si_refuse = ?, statut_final = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resultat.getResultat());
            pstmt.setString(2, resultat.getDecision_si_refuse());
            pstmt.setString(3, resultat.getStatut_final());
            pstmt.setInt(4, resultat.getId());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur mise à jour résultat examen : " + e.getMessage());
        }
    }

    /**
     * Vérifie si un résultat existe pour cet étudiant et année
     */
    public static boolean existe(int etudiantId, String annee) {
        String sql = "SELECT COUNT(*) FROM resultats_examen_national WHERE etudiant_id = ? AND annee_scolaire = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, etudiantId);
            pstmt.setString(2, annee);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Supprime un résultat (rare, mais possible pour correction)
     */
    public static void supprimer(int id) {
        String sql = "DELETE FROM resultats_examen_national WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur suppression résultat examen : " + e.getMessage());
        }
    }

    private static ResultatExamenNational mapperResultSet(ResultSet rs) throws Exception {
        ResultatExamenNational resultat = new ResultatExamenNational();
        resultat.setId(rs.getInt("id"));
        resultat.setEtudiantId(rs.getInt("etudiant_id"));
        resultat.setClasseId(rs.getInt("classe_id"));
        resultat.setAnnee_scolaire(rs.getString("annee_scolaire"));
        resultat.setNomExamen(rs.getString("nom_examen"));
        resultat.setResultat(rs.getString("resultat"));
        resultat.setDecision_si_refuse(rs.getString("decision_si_refuse"));
        resultat.setStatut_final(rs.getString("statut_final"));
        resultat.setSaisi_par(rs.getString("saisi_par"));
        resultat.setDate_saisie(rs.getObject("date_saisie", LocalDateTime.class));

        // Ajouter le nom étudiant et classe s'ils sont dans le ResultSet (requête JOIN)
        try {
            String nomComplet = rs.getString("prenom") + " " + rs.getString("nom");
            resultat.setNomEtudiant(nomComplet.trim());
        } catch (Exception ignored) {}

        try {
            resultat.setClasseNom(rs.getString("classe_nom"));
        } catch (Exception ignored) {}

        return resultat;
    }
}
