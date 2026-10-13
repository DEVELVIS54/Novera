package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.repositories.SyncRepository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Indicateurs affichés sur le tableau de bord (lecture seule, en local). */
public class DashboardService {

    public record EffectifClasse(String classe, int effectif) {}

    public record Resume(
        int elevesActifs,
        int classes,
        int matieres,
        int parentsActifs,
        int elevesSubventionnes,
        double totalEncaisse,
        int operationsEnAttente,
        List<EffectifClasse> effectifs
    ) {}

    public Resume charger() {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            int eleves = compter(stmt, "SELECT COUNT(*) FROM etudiants WHERE statut_scolarite = 'actif'");
            int classes = compter(stmt, "SELECT COUNT(*) FROM classes");
            int matieres = compter(stmt, "SELECT COUNT(*) FROM matieres");
            int parents = compter(stmt, "SELECT COUNT(*) FROM parents WHERE actif = 1");
            int subventionnes = compter(stmt,
                "SELECT COUNT(*) FROM etudiants WHERE statut_scolarite = 'actif' "
                + "AND affecte_etat = 1 AND subvention_active = 1");

            double encaisse = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(montant), 0) FROM paiements")) {
                if (rs.next()) encaisse = rs.getDouble(1);
            }

            List<EffectifClasse> effectifs = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT c.nom, COUNT(e.id) AS effectif FROM classes c "
                    + "LEFT JOIN etudiants e ON e.classe_id = c.id AND e.statut_scolarite = 'actif' "
                    + "GROUP BY c.id, c.nom ORDER BY c.nom")) {
                while (rs.next()) {
                    effectifs.add(new EffectifClasse(rs.getString("nom"), rs.getInt("effectif")));
                }
            }

            int enAttente = new SyncRepository().compterEnAttente();

            return new Resume(eleves, classes, matieres, parents, subventionnes, encaisse, enAttente, effectifs);

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement du tableau de bord : " + e.getMessage(), e);
        }
    }

    private int compter(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
