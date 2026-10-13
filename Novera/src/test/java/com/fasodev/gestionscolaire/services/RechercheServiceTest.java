package com.fasodev.gestionscolaire.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RechercheServiceTest {

    @Test
    void motif_entoure_le_texte_et_gere_null() {
        assertEquals("%awa%", RechercheService.motif("  awa "));
        assertEquals("%%", RechercheService.motif(null));
    }

    @Test
    void libelles_de_statut() {
        assertEquals("Actif", RechercheService.libelleStatut("actif"));
        assertEquals("Sorti", RechercheService.libelleStatut("sorti"));
        assertEquals("En attente de réinscription", RechercheService.libelleStatut("en_attente_reinscription"));
        assertEquals("", RechercheService.libelleStatut(null));
    }
}
