package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.ResultatPassage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès à la table `resultats_annuels` (résultats de passage en classe supérieure).
 */
public class ResultatPassageRepository {

    /**
     * Crée un nouvel enregistrement de résultat annuel
     */
    public static void creer(ResultatPassage resultat) {
        String sql = """
            INSERT INTO resultats_annuels 
            (etudiant_id, annee_scolaire, moyenne_t1, moyenne_t2, moyenne_t3, 
             moyenne_annuelle, statut_calcule, statut_final, classe_origine_id, 
             classe_suivante_id, valide_par, date_validation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, resultat.getEtudiantId());
            pstmt.setString(2, resultat.getAnnee_scolaire());
            pstmt.setDouble(3, resultat.getMoyenneT1());
            pstmt.setDouble(4, resultat.getMoyenneT2());
            pstmt.setDouble(5, resultat.getMoyenneT3());
            pstmt.setDouble(6, resultat.getMoyenneAnnuelle());
            pstmt.setString(7, resultat.getStatutCalcule());
            pstmt.setString(8, resultat.getStatutFinal());
            pstmt.setObject(9, resultat.getClasseOriginId() > 0 ? resultat.getClasseOriginId() : null);
            pstmt.setObject(10, resultat.getClasseSuivanteId() > 0 ? resultat.getClasseSuivanteId() : null);
            pstmt.setString(11, resultat.getValideePar());
            pstmt.setObject(12, resultat.getDateValidation());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    resultat.setId(rs.getInt(1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur création résultat passage : " + e.getMessage());
        }
    }

    /**
     * Récupère un résultat par ID
     */
    public static ResultatPassage obtenirParId(int id) {
        String sql = "SELECT * FROM resultats_annuels WHERE id = ?";

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
     * Récupère tous les résultats pour un étudiant
     */
    public static List<ResultatPassage> obtenirParEtudiant(int etudiantId) {
        List<ResultatPassage> resultats = new ArrayList<>();
        String sql = "SELECT * FROM resultats_annuels WHERE etudiant_id = ? ORDER BY annee_scolaire DESC";

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
     * Récupère tous les résultats pour une classe et année donnée
     */
    public static List<ResultatPassage> obtenirParClasse(int classeId, String annee_scolaire) {
        List<ResultatPassage> resultats = new ArrayList<>();
        String sql = """
            SELECT ra.* FROM resultats_annuels ra
            JOIN etudiants e ON ra.etudiant_id = e.id
            WHERE e.classe_id = ? AND ra.annee_scolaire = ?
            ORDER BY e.nom, e.prenom
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, classeId);
            pstmt.setString(2, annee_scolaire);

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
     * Récupère tous les résultats en délibération pour une classe
     */
    public static List<ResultatPassage> obtenirDeliberations(int classeId, String annee_scolaire) {
        List<ResultatPassage> resultats = new ArrayList<>();
        String sql = """
            SELECT ra.* FROM resultats_annuels ra
            JOIN etudiants e ON ra.etudiant_id = e.id
            WHERE e.classe_id = ? AND ra.annee_scolaire = ? AND ra.statut_calcule = 'DELIBERATION'
            ORDER BY e.nom, e.prenom
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, classeId);
            pstmt.setString(2, annee_scolaire);

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
     * Met à jour un résultat (notamment le statut final après délibération)
     */
    public static void mettreAJour(ResultatPassage resultat) {
        String sql = """
            UPDATE resultats_annuels
            SET moyenne_t1 = ?, moyenne_t2 = ?, moyenne_t3 = ?,
                moyenne_annuelle = ?, statut_calcule = ?, statut_final = ?,
                classe_origine_id = ?, classe_suivante_id = ?,
                valide_par = ?, date_validation = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, resultat.getMoyenneT1());
            pstmt.setDouble(2, resultat.getMoyenneT2());
            pstmt.setDouble(3, resultat.getMoyenneT3());
            pstmt.setDouble(4, resultat.getMoyenneAnnuelle());
            pstmt.setString(5, resultat.getStatutCalcule());
            pstmt.setString(6, resultat.getStatutFinal());
            pstmt.setObject(7, resultat.getClasseOriginId() > 0 ? resultat.getClasseOriginId() : null);
            pstmt.setObject(8, resultat.getClasseSuivanteId() > 0 ? resultat.getClasseSuivanteId() : null);
            pstmt.setString(9, resultat.getValideePar());
            pstmt.setObject(10, resultat.getDateValidation());
            pstmt.setInt(11, resultat.getId());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur mise à jour résultat passage : " + e.getMessage());
        }
    }

    /**
     * Supprime un résultat
     */
    public static void supprimer(int id) {
        String sql = "DELETE FROM resultats_annuels WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur suppression résultat passage : " + e.getMessage());
        }
    }

    /**
     * Vérifie si un résultat existe déjà pour un étudiant + année
     */
    public static boolean existe(int etudiantId, String annee_scolaire) {
        String sql = "SELECT COUNT(*) FROM resultats_annuels WHERE etudiant_id = ? AND annee_scolaire = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, etudiantId);
            pstmt.setString(2, annee_scolaire);

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

    private static ResultatPassage mapperResultSet(ResultSet rs) throws Exception {
        ResultatPassage resultat = new ResultatPassage();
        resultat.setId(rs.getInt("id"));
        resultat.setEtudiantId(rs.getInt("etudiant_id"));
        resultat.setAnnee_scolaire(rs.getString("annee_scolaire"));
        resultat.setMoyenneT1(rs.getDouble("moyenne_t1"));
        resultat.setMoyenneT2(rs.getDouble("moyenne_t2"));
        resultat.setMoyenneT3(rs.getDouble("moyenne_t3"));
        resultat.setMoyenneAnnuelle(rs.getDouble("moyenne_annuelle"));
        resultat.setStatutCalcule(rs.getString("statut_calcule"));
        resultat.setStatutFinal(rs.getString("statut_final"));
        resultat.setClasseOriginId(rs.getInt("classe_origine_id"));
        resultat.setClasseSuivanteId(rs.getInt("classe_suivante_id"));
        resultat.setValideePar(rs.getString("valide_par"));
        resultat.setDateValidation(rs.getObject("date_validation", LocalDateTime.class));
        return resultat;
    }
}
