package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.models.Reinscription;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReinscriptionRepository {

    /**
     * Crée une nouvelle demande de réinscription
     */
    public static void creer(Reinscription reinscription) {
        String sql = """
            INSERT INTO reinscriptions 
            (etudiant_id, resultat_examen_id, nouvelle_classe_id, annee_scolaire, statut)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, reinscription.getEtudiantId());
            pstmt.setInt(2, reinscription.getResultat_examen_id());
            pstmt.setInt(3, reinscription.getNouvelle_classe_id());
            pstmt.setString(4, reinscription.getAnnee_scolaire());
            pstmt.setString(5, reinscription.getStatut());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    reinscription.setId(rs.getInt(1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur création réinscription : " + e.getMessage());
        }
    }

    /**
     * Récupère une réinscription par ID
     */
    public static Reinscription obtenirParId(int id) {
        String sql = "SELECT * FROM reinscriptions WHERE id = ?";

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
     * Récupère les réinscriptions en attente (non confirmées)
     */
    public static List<Reinscription> obtenirEnAttente(String annee) {
        List<Reinscription> resultats = new ArrayList<>();
        String sql = """
            SELECT r.*, e.nom, e.prenom, c.nom as classe_nom
            FROM reinscriptions r
            JOIN etudiants e ON r.etudiant_id = e.id
            JOIN classes c ON r.nouvelle_classe_id = c.id
            WHERE r.statut = 'en_attente' AND r.annee_scolaire = ?
            ORDER BY r.created_at DESC
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
     * Récupère toutes les réinscriptions confirmées
     */
    public static List<Reinscription> obtenirConfirmees(String annee) {
        List<Reinscription> resultats = new ArrayList<>();
        String sql = """
            SELECT r.*, e.nom, e.prenom, c.nom as classe_nom
            FROM reinscriptions r
            JOIN etudiants e ON r.etudiant_id = e.id
            JOIN classes c ON r.nouvelle_classe_id = c.id
            WHERE r.statut = 'confirmee' AND r.annee_scolaire = ?
            ORDER BY r.date_confirmation DESC
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
     * Récupère la réinscription pour un étudiant
     */
    public static Reinscription obtenirParEtudiant(int etudiantId, String annee) {
        String sql = """
            SELECT * FROM reinscriptions 
            WHERE etudiant_id = ? AND annee_scolaire = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, etudiantId);
            pstmt.setString(2, annee);

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
     * Met à jour une réinscription (confirmation)
     */
    public static void confirmer(Reinscription reinscription) {
        String sql = """
            UPDATE reinscriptions
            SET statut = 'confirmee', confirmee_par = ?, date_confirmation = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, reinscription.getConfirmee_par());
            pstmt.setObject(2, reinscription.getDate_confirmation());
            pstmt.setInt(3, reinscription.getId());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur confirmation réinscription : " + e.getMessage());
        }
    }

    /**
     * Refuse une réinscription (élève parti)
     */
    public static void refuser(int reinscriptionId) {
        String sql = """
            UPDATE reinscriptions
            SET statut = 'refusee'
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, reinscriptionId);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur refus réinscription : " + e.getMessage());
        }
    }

    /**
     * Vérifie si une réinscription existe pour cet étudiant
     */
    public static boolean existe(int etudiantId, String annee) {
        String sql = "SELECT COUNT(*) FROM reinscriptions WHERE etudiant_id = ? AND annee_scolaire = ?";

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

    private static Reinscription mapperResultSet(ResultSet rs) throws Exception {
        Reinscription reinscription = new Reinscription();
        reinscription.setId(rs.getInt("id"));
        reinscription.setEtudiantId(rs.getInt("etudiant_id"));
        reinscription.setResultat_examen_id(rs.getInt("resultat_examen_id"));
        reinscription.setNouvelle_classe_id(rs.getInt("nouvelle_classe_id"));
        reinscription.setAnnee_scolaire(rs.getString("annee_scolaire"));
        reinscription.setStatut(rs.getString("statut"));
        reinscription.setConfirmee_par(rs.getString("confirmee_par"));
        reinscription.setDate_confirmation(rs.getObject("date_confirmation", LocalDateTime.class));
        reinscription.setCreated_at(rs.getObject("created_at", LocalDateTime.class));

        // Ajouter nom étudiant et classe s'ils sont présents (requête JOIN)
        try {
            String nomComplet = rs.getString("prenom") + " " + rs.getString("nom");
            reinscription.setNomEtudiant(nomComplet.trim());
        } catch (Exception ignored) {}

        try {
            reinscription.setNouvelle_classe_nom(rs.getString("classe_nom"));
        } catch (Exception ignored) {}

        return reinscription;
    }
}
