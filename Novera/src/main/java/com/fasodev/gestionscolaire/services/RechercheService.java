package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Recherche rapide d'un élève (nom, prénom, matricule) ou d'une classe (nom, niveau). */
public class RechercheService {

    private static final int LIMITE = 100;

    public record EleveTrouve(int id, String matricule, String nomComplet, String classe, String statut) {}

    public record ClasseTrouvee(int id, String nom, String niveau, int effectif) {}

    public List<EleveTrouve> chercherEleves(String texte) {
        String motif = motif(texte);
        String sql = """
            SELECT e.id, e.matricule, e.prenom, e.nom, c.nom AS classe, e.statut_scolarite
            FROM etudiants e
            JOIN classes c ON c.id = e.classe_id
            WHERE e.nom LIKE ? OR e.prenom LIKE ? OR e.matricule LIKE ?
               OR (e.prenom || ' ' || e.nom) LIKE ?
            ORDER BY e.nom, e.prenom
            LIMIT ?
        """;

        List<EleveTrouve> resultat = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, motif);
            stmt.setString(2, motif);
            stmt.setString(3, motif);
            stmt.setString(4, motif);
            stmt.setInt(5, LIMITE);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resultat.add(new EleveTrouve(
                    rs.getInt("id"),
                    rs.getString("matricule"),
                    rs.getString("prenom") + " " + rs.getString("nom"),
                    rs.getString("classe"),
                    libelleStatut(rs.getString("statut_scolarite"))));
            }
            return resultat;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur de recherche : " + e.getMessage(), e);
        }
    }

    public List<ClasseTrouvee> chercherClasses(String texte) {
        String motif = motif(texte);
        String sql = """
            SELECT c.id, c.nom, c.niveau, COUNT(e.id) AS effectif
            FROM classes c
            LEFT JOIN etudiants e ON e.classe_id = c.id AND e.statut_scolarite = 'actif'
            WHERE c.nom LIKE ? OR c.niveau LIKE ?
            GROUP BY c.id, c.nom, c.niveau
            ORDER BY c.nom
            LIMIT ?
        """;

        List<ClasseTrouvee> resultat = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, motif);
            stmt.setString(2, motif);
            stmt.setInt(3, LIMITE);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resultat.add(new ClasseTrouvee(
                    rs.getInt("id"), rs.getString("nom"), rs.getString("niveau"), rs.getInt("effectif")));
            }
            return resultat;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur de recherche : " + e.getMessage(), e);
        }
    }

    static String motif(String texte) {
        return "%" + (texte == null ? "" : texte.trim()) + "%";
    }

    static String libelleStatut(String statut) {
        if (statut == null) return "";
        return switch (statut) {
            case "actif" -> "Actif";
            case "parti" -> "Parti";
            case "sorti" -> "Sorti";
            case "en_attente_reinscription" -> "En attente de réinscription";
            default -> statut;
        };
    }
}
