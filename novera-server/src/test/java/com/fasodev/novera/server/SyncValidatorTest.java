package com.fasodev.novera.server;

import com.fasodev.novera.server.sync.SyncValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncValidatorTest {

    @Test
    void operation_valide_est_acceptee() {
        assertNull(SyncValidator.valider("etudiants", "INSERT", 12, true));
        assertNull(SyncValidator.valider("paiements", "UPDATE", 1, true));
    }

    @Test
    void suppression_n_exige_pas_de_contenu() {
        assertNull(SyncValidator.valider("notes", "DELETE", 5, false));
    }

    @Test
    void table_non_autorisee_est_refusee() {
        assertNotNull(SyncValidator.valider("utilisateurs", "INSERT", 1, true));
        assertNotNull(SyncValidator.valider("sync_records; DROP TABLE x", "INSERT", 1, true));
        assertNotNull(SyncValidator.valider(null, "INSERT", 1, true));
    }

    @Test
    void operation_inconnue_ou_identifiant_invalide_est_refuse() {
        assertNotNull(SyncValidator.valider("classes", "TRUNCATE", 1, true));
        assertNotNull(SyncValidator.valider("classes", "INSERT", 0, true));
        assertNotNull(SyncValidator.valider("classes", "INSERT", -4, true));
    }

    @Test
    void insert_ou_update_sans_contenu_est_refuse() {
        assertTrue(SyncValidator.valider("classes", "UPDATE", 3, false).contains("manquant"));
    }

    @Test
    void les_comptes_utilisateurs_ne_sont_pas_synchronisables() {
        assertTrue(!SyncValidator.TABLES_AUTORISEES.contains("utilisateurs"));
    }
}
