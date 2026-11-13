package com.fasodev.novera.server;

import com.fasodev.novera.server.records.ChampsSensibles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChampsSensiblesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void le_hash_du_mot_de_passe_d_un_parent_n_est_jamais_expose() throws Exception {
        String payload = "{\"id\":1,\"identifiant\":\"LSJK-6A-0042\",\"password_hash\":\"$2a$10$abc\",\"actif\":1}";

        JsonNode resultat = json.readTree(ChampsSensibles.masquer("parents", payload));

        assertFalse(resultat.has("password_hash"));
        assertEquals("LSJK-6A-0042", resultat.get("identifiant").asText());
        assertEquals(1, resultat.get("actif").asInt());
    }

    @Test
    void les_parametres_de_connexion_de_l_ecole_sont_retires() throws Exception {
        String payload = "{\"nom\":\"Mon Ecole\",\"api_key\":\"secret\",\"api_base_url\":\"https://x\",\"device_id\":\"d1\"}";

        JsonNode resultat = json.readTree(ChampsSensibles.masquer("configuration_ecole", payload));

        assertFalse(resultat.has("api_key"));
        assertFalse(resultat.has("api_base_url"));
        assertFalse(resultat.has("device_id"));
        assertEquals("Mon Ecole", resultat.get("nom").asText());
    }

    @Test
    void les_autres_tables_sont_inchangees() {
        String payload = "{\"id\":7,\"valeur\":12.5}";

        assertEquals(payload, ChampsSensibles.masquer("notes", payload));
    }

    @Test
    void un_json_illisible_d_une_table_sensible_n_expose_rien() {
        assertEquals("{}", ChampsSensibles.masquer("parents", "pas du json {"));
        assertEquals("{}", ChampsSensibles.masquer("parents", "[1,2]"));
    }

    @Test
    void un_payload_nul_reste_nul() {
        assertTrue(ChampsSensibles.masquer("parents", null) == null);
    }
}