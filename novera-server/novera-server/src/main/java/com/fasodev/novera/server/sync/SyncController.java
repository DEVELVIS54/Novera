package com.fasodev.novera.server.sync;

import com.fasodev.novera.server.sync.SyncModels.Reponse;
import com.fasodev.novera.server.sync.SyncModels.Requete;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    /** Public : sert au desktop à savoir si le serveur est joignable. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** Protégé par la clé d'accès : sert au test de connexion du desktop. */
    @GetMapping("/api/sync/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/sync/batch")
    public Reponse batch(@RequestBody Requete requete) {
        return service.appliquer(requete);
    }
}
