package com.fasodev.novera.server.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

/**
 * Retire des données les champs qui ne doivent jamais sortir par l'API de lecture
 * (hash des mots de passe des parents, paramètres de connexion de l'école).
 */
public final class ChampsSensibles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, Set<String>> MASQUES = Map.of(
        "parents", Set.of("password_hash", "mot_de_passe_hash"),
        "configuration_ecole", Set.of("api_key", "api_base_url", "device_id")
    );

    private ChampsSensibles() {
    }

    /**
     * @return le JSON sans les champs sensibles de la table ; inchangé pour les autres tables ;
     *         {@code "{}"} si le JSON d'une table sensible est illisible (on n'expose rien).
     */
    public static String masquer(String table, String payloadJson) {
        Set<String> champs = MASQUES.get(table);
        if (champs == null || payloadJson == null) {
            return payloadJson;
        }
        try {
            JsonNode noeud = JSON.readTree(payloadJson);
            if (noeud instanceof ObjectNode objet) {
                objet.remove(champs);
                return JSON.writeValueAsString(objet);
            }
            return "{}";
        } catch (Exception e) {
            return "{}";
        }
    }
}