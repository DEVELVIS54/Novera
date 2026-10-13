package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accès à la file de synchronisation (table sync_queue) et aux paramètres
 * de connexion au serveur (stockés dans configuration_ecole).
 */
public class SyncRepository {

    /** Colonnes locales qui ne doivent jamais quitter le poste. */
    private static final Map<String, Set<String>> COLONNES_EXCLUES = Map.of(
        "configuration_ecole", Set.of("api_key", "api_base_url", "device_id", "logo_path")
    );

    public record ConfigSync(String urlServeur, String cleApi, String deviceId) {
        public boolean estConfigure() {
            return urlServeur != null && !urlServeur.isBlank()
                && cleApi != null && !cleApi.isBlank();
        }
    }

    public record OperationEnFile(long id, String operation, String table, long recordId) {}

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ------------------------------------------------------------------
    // Paramètres serveur
    // ------------------------------------------------------------------

    public ConfigSync lireConfig() {
        String sql = "SELECT api_base_url, api_key, device_id FROM configuration_ecole WHERE id = 1";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String url = null, cle = null, device = null;
            if (rs.next()) {
                url = rs.getString("api_base_url");
                cle = rs.getString("api_key");
                device = rs.getString("device_id");
            }
            if (device == null || device.isBlank()) {
                device = UUID.randomUUID().toString();
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE configuration_ecole SET device_id = ? WHERE id = 1")) {
                    up.setString(1, device);
                    up.executeUpdate();
                }
            }
            return new ConfigSync(url, cle, device);

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture paramètres de synchronisation : " + e.getMessage(), e);
        }
    }

    public void enregistrerConfig(String urlServeur, String cleApi) {
        String sql = "UPDATE configuration_ecole SET api_base_url = ?, api_key = ? WHERE id = 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, urlServeur);
            stmt.setString(2, cleApi);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur enregistrement paramètres de synchronisation : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // File d'attente
    // ------------------------------------------------------------------

    public int compterEnAttente() {
        String sql = "SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture file de synchronisation : " + e.getMessage(), e);
        }
    }

    public int compterEnEchec() {
        String sql = "SELECT COUNT(*) FROM sync_queue WHERE status = 'failed'";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture file de synchronisation : " + e.getMessage(), e);
        }
    }

    public List<OperationEnFile> lireEnAttente(int limite) {
        String sql = "SELECT id, operation, table_name, record_id FROM sync_queue "
                   + "WHERE status = 'pending' ORDER BY id LIMIT ?";
        List<OperationEnFile> resultat = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                resultat.add(new OperationEnFile(
                    rs.getLong("id"), rs.getString("operation"),
                    rs.getString("table_name"), rs.getLong("record_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture file de synchronisation : " + e.getMessage(), e);
        }
        return resultat;
    }

    public void marquerEnvoyees(List<Long> ids) {
        changerStatut(ids, "synced", true);
    }

    public void marquerEnEchec(List<Long> ids) {
        changerStatut(ids, "failed", false);
    }

    /** Remet en attente les opérations refusées, pour une nouvelle tentative manuelle. */
    public void reessayerEchecs() {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE sync_queue SET status = 'pending' WHERE status = 'failed'");
        } catch (SQLException e) {
            throw new RuntimeException("Erreur remise en attente : " + e.getMessage(), e);
        }
    }

    private void changerStatut(List<Long> ids, String statut, boolean datee) {
        if (ids.isEmpty()) return;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) in.append(i == 0 ? "?" : ",?");
        String sql = "UPDATE sync_queue SET status = ?"
                   + (datee ? ", synced_at = CURRENT_TIMESTAMP" : "")
                   + " WHERE id IN (" + in + ")";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, statut);
            for (int i = 0; i < ids.size(); i++) stmt.setLong(i + 2, ids.get(i));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour file de synchronisation : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Lecture d'une ligne à envoyer
    // ------------------------------------------------------------------

    /**
     * Lit l'état actuel d'une ligne (colonne → valeur), sans les colonnes
     * locales sensibles. Retourne null si la ligne n'existe plus.
     * La table doit figurer dans la liste des tables synchronisées.
     */
    public Map<String, Object> lireLigne(String table, long id) {
        if (!DatabaseInitializer.TABLES_SYNCHRONISEES.contains(table)) {
            throw new IllegalArgumentException("Table non synchronisable : " + table);
        }
        Set<String> exclues = COLONNES_EXCLUES.getOrDefault(table, Set.of());

        String sql = "SELECT * FROM " + table + " WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return null;

            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> ligne = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String colonne = meta.getColumnName(i);
                if (exclues.contains(colonne) || "sync_status".equals(colonne)) continue;
                ligne.put(colonne, rs.getObject(i));
            }
            return ligne;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture ligne " + table + "#" + id + " : " + e.getMessage(), e);
        }
    }
}
