package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.repositories.SyncRepository;
import com.fasodev.gestionscolaire.repositories.SyncRepository.ConfigSync;
import com.fasodev.gestionscolaire.repositories.SyncRepository.OperationEnFile;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Envoie au serveur les changements enregistrés dans sync_queue.
 *
 * Principe : l'application fonctionne toujours en local. Les triggers SQLite
 * notent chaque insertion, modification ou suppression ; ce service lit la file
 * par lots, joint l'état actuel de chaque ligne et le pousse vers
 * POST {serveur}/api/sync/batch. Le serveur répond avec les opérations
 * acceptées et refusées. En cas de coupure, la file reste intacte.
 */
public class SyncService {

    static final int TAILLE_LOT = 100;
    static final int LOTS_MAX_PAR_PASSAGE = 20;

    /** Résultat d'un passage de synchronisation. */
    public record Resultat(boolean serveurJoignable, int envoyees, int refusees, int restantes, String message) {}

    private final SyncRepository repository = new SyncRepository();
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /** Vérifie que le serveur répond (GET /api/health, sans clé). */
    public boolean serveurJoignable(ConfigSync config) {
        if (config == null || config.urlServeur() == null || config.urlServeur().isBlank()) return false;
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(base(config) + "/api/health"))
                .timeout(Duration.ofSeconds(5)).GET().build();
            return http.send(requete, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Teste l'URL et la clé : retourne null si tout va bien, sinon un message d'erreur. */
    public String tester(String url, String cle) {
        ConfigSync config = new ConfigSync(url, cle, "test");
        if (!config.estConfigure()) return "Renseigne l'adresse du serveur et la clé d'accès.";
        if (!serveurJoignable(config)) return "Serveur injoignable à cette adresse.";
        try {
            HttpRequest requete = HttpRequest.newBuilder(URI.create(base(config) + "/api/sync/ping"))
                .timeout(Duration.ofSeconds(5)).header("X-Api-Key", cle).GET().build();
            int code = http.send(requete, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code == 200) return null;
            if (code == 401) return "Clé d'accès refusée par le serveur.";
            return "Réponse inattendue du serveur (code " + code + ").";
        } catch (IOException | IllegalArgumentException e) {
            return "Erreur de connexion : " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Test interrompu.";
        }
    }

    /** Un passage complet : envoie tous les lots en attente (dans la limite fixée). */
    public synchronized Resultat synchroniser() {
        ConfigSync config = repository.lireConfig();

        if (!config.estConfigure()) {
            return new Resultat(false, 0, 0, repository.compterEnAttente(),
                "Synchronisation non configurée.");
        }
        if (!serveurJoignable(config)) {
            return new Resultat(false, 0, 0, repository.compterEnAttente(), "Serveur injoignable.");
        }

        int envoyees = 0, refusees = 0;
        for (int lot = 0; lot < LOTS_MAX_PAR_PASSAGE; lot++) {
            List<OperationEnFile> enAttente = repository.lireEnAttente(TAILLE_LOT);
            if (enAttente.isEmpty()) break;

            try {
                int[] bilan = envoyerLot(config, enAttente);
                envoyees += bilan[0];
                refusees += bilan[1];
            } catch (IOException | IllegalStateException e) {
                return new Resultat(false, envoyees, refusees, repository.compterEnAttente(),
                    "Envoi interrompu : " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Resultat(false, envoyees, refusees, repository.compterEnAttente(), "Envoi interrompu.");
            }
        }

        int restantes = repository.compterEnAttente();
        String message = envoyees + " opération(s) envoyée(s)"
            + (refusees > 0 ? ", " + refusees + " refusée(s)" : "")
            + (restantes > 0 ? ", " + restantes + " en attente." : ".");
        return new Resultat(true, envoyees, refusees, restantes, message);
    }

    /** Envoie un lot ; retourne {acceptées, refusées}. */
    private int[] envoyerLot(ConfigSync config, List<OperationEnFile> lot)
            throws IOException, InterruptedException {

        List<Long> aIgnorer = new ArrayList<>();
        JsonArray operations = new JsonArray();

        for (OperationEnFile op : lot) {
            JsonObject json = new JsonObject();
            json.addProperty("queueId", op.id());
            json.addProperty("table", op.table());
            json.addProperty("recordId", op.recordId());

            if ("DELETE".equals(op.operation())) {
                json.addProperty("operation", "DELETE");
            } else {
                Map<String, Object> ligne = repository.lireLigne(op.table(), op.recordId());
                if (ligne == null) {
                    // La ligne a disparu depuis : une opération DELETE suit dans la file.
                    aIgnorer.add(op.id());
                    continue;
                }
                json.addProperty("operation", op.operation());
                json.add("payload", gson.toJsonTree(ligne));
            }
            operations.add(json);
        }

        repository.marquerEnvoyees(aIgnorer);
        if (operations.size() == 0) return new int[] {0, 0};

        JsonObject corps = new JsonObject();
        corps.addProperty("deviceId", config.deviceId());
        corps.add("operations", operations);

        HttpRequest requete = HttpRequest.newBuilder(URI.create(base(config) + "/api/sync/batch"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", config.cleApi())
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(corps)))
            .build();

        HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
        if (reponse.statusCode() == 401) {
            throw new IllegalStateException("clé d'accès refusée");
        }
        if (reponse.statusCode() != 200) {
            throw new IllegalStateException("code HTTP " + reponse.statusCode());
        }

        JsonObject resultat = gson.fromJson(reponse.body(), JsonObject.class);
        List<Long> acceptees = new ArrayList<>();
        List<Long> rejetees = new ArrayList<>();

        if (resultat.has("accepted")) {
            for (JsonElement e : resultat.getAsJsonArray("accepted")) acceptees.add(e.getAsLong());
        }
        if (resultat.has("rejected")) {
            for (JsonElement e : resultat.getAsJsonArray("rejected")) {
                rejetees.add(e.getAsJsonObject().get("queueId").getAsLong());
            }
        }

        repository.marquerEnvoyees(acceptees);
        repository.marquerEnEchec(rejetees);
        return new int[] {acceptees.size(), rejetees.size()};
    }

    private static String base(ConfigSync config) {
        String url = config.urlServeur().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
