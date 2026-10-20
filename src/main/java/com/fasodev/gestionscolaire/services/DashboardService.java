package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.repositories.SyncRepository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Indicateurs du tableau de bord (lecture seule, calculés en local). */
public class DashboardService {

    /** Répartition des élèves par statut. */
    public record Statuts(int actifs, int enAttenteReinscription, int sortis, int partis) {
        public int total() {
            return actifs + enAttenteReinscription + sortis + partis;
        }
    }

    /** Une classe avec son effectif et la moyenne de son dernier trimestre calculé (null si aucune). */
    public record ClasseClassee(String nom, String niveau, int effectif, Double moyenne) {}

    /** Une chose à faire, avec la page vers laquelle elle mène. */
    public record Alerte(String icone, String texte, String cible) {}

    public record Resume(
        int elevesActifs,
        int classes,
        int matieres,
        int parentsActifs,
        int elevesSubventionnes,
        double totalEncaisse,
        int operationsEnAttente,
        Double moyenneGenerale,
        Statuts statuts,
        List<ClasseClassee> classement,
        List<Alerte> alertes
    ) {}

    public Resume charger(boolean estAdmin) {
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

            Statuts statuts = new Statuts(
                eleves,
                compter(stmt, "SELECT COUNT(*) FROM etudiants WHERE statut_scolarite = 'en_attente_reinscription'"),
                compter(stmt, "SELECT COUNT(*) FROM etudiants WHERE statut_scolarite = 'sorti'"),
                compter(stmt, "SELECT COUNT(*) FROM etudiants WHERE statut_scolarite = 'parti'"));

            List<ClasseClassee> toutes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT c.nom, c.niveau,
                        (SELECT COUNT(*) FROM etudiants e
                          WHERE e.classe_id = c.id AND e.statut_scolarite = 'actif') AS effectif,
                        (SELECT AVG(m.moyenne) FROM moyennes_trimestrielles m
                          WHERE m.classe_id = c.id
                            AND m.trimestre = (SELECT MAX(m2.trimestre) FROM moyennes_trimestrielles m2
                                                WHERE m2.classe_id = c.id)) AS moyenne
                    FROM classes c
                    ORDER BY (moyenne IS NULL), moyenne DESC, c.nom
                    """)) {
                while (rs.next()) {
                    double moy = rs.getDouble("moyenne");
                    boolean sansMoyenne = rs.wasNull();   // à lire juste après getDouble
                    toutes.add(new ClasseClassee(
                        rs.getString("nom"), rs.getString("niveau"), rs.getInt("effectif"),
                        sansMoyenne ? null : moy));
                }
            }

            SyncRepository sync = new SyncRepository();
            int enAttente = sync.compterEnAttente();

            List<Alerte> alertes = new ArrayList<>();
            if (statuts.enAttenteReinscription() > 0) {
                alertes.add(new Alerte("refresh",
                    pluriel(statuts.enAttenteReinscription(), "élève attend", "élèves attendent")
                        + " une réinscription", "reinscriptions"));
            }
            int sansMoyennes = compter(stmt, """
                SELECT COUNT(*) FROM classes c
                WHERE EXISTS (SELECT 1 FROM etudiants e WHERE e.classe_id = c.id AND e.statut_scolarite = 'actif')
                  AND NOT EXISTS (SELECT 1 FROM calcul_moyennes_trimestre k
                                   WHERE k.classe_id = c.id AND k.statut = 'calcule_verrouille')
                """);
            if (sansMoyennes > 0) {
                alertes.add(new Alerte("chart",
                    pluriel(sansMoyennes, "classe n'a", "classes n'ont") + " pas encore de moyennes calculées",
                    "moyennes"));
            }
            int sansAcces = compter(stmt, """
                SELECT COUNT(*) FROM etudiants e
                WHERE e.statut_scolarite = 'actif'
                  AND NOT EXISTS (SELECT 1 FROM parents p WHERE p.etudiant_id = e.id)
                """);
            if (sansAcces > 0) {
                alertes.add(new Alerte("key",
                    pluriel(sansAcces, "élève n'a", "élèves n'ont") + " pas d'accès parent", "parents"));
            }
            if (estAdmin) {
                if (!sync.lireConfig().estConfigure()) {
                    alertes.add(new Alerte("cloud", "Le serveur n'est pas encore configuré", "synchronisation"));
                } else if (enAttente > 0) {
                    alertes.add(new Alerte("cloud",
                        pluriel(enAttente, "modification attend", "modifications attendent") + " d'être envoyée(s)",
                        "synchronisation"));
                }
            }

            Double moyenneGenerale = null;
            double somme = 0;
            int n = 0;
            for (ClasseClassee c : toutes) {
                if (c.moyenne() != null) { somme += c.moyenne(); n++; }
            }
            if (n > 0) moyenneGenerale = somme / n;

            List<ClasseClassee> classement = toutes.size() > 6 ? toutes.subList(0, 6) : toutes;

            return new Resume(eleves, classes, matieres, parents, subventionnes, encaisse,
                enAttente, moyenneGenerale, statuts, new ArrayList<>(classement), alertes);

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement du tableau de bord : " + e.getMessage(), e);
        }
    }

    static String pluriel(int n, String singulier, String pluriel) {
        return n + " " + (n == 1 ? singulier : pluriel);
    }

    private int compter(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
