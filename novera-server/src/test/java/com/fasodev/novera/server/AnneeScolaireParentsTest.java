package com.fasodev.novera.server;

import com.fasodev.novera.server.parents.AnneeScolaireParents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnneeScolaireParentsTest {

    @Test
    void le_format_doit_etre_deux_annees_consecutives() {
        assertTrue(AnneeScolaireParents.estValide("2026-2027"));
        assertFalse(AnneeScolaireParents.estValide("2026-2028"));
        assertFalse(AnneeScolaireParents.estValide("2026"));
        assertFalse(AnneeScolaireParents.estValide(""));
        assertFalse(AnneeScolaireParents.estValide(null));
    }

    @Test
    void l_annee_active_de_l_ecole_est_prioritaire() {
        assertEquals("2026-2027",
            AnneeScolaireParents.choisir("2026-2027", List.of("2027-2028", "2025-2026")));
    }

    @Test
    void sans_annee_active_on_prend_la_plus_recente_des_donnees() {
        assertEquals("2027-2028",
            AnneeScolaireParents.choisir("", List.of("2025-2026", "2027-2028", "2026-2027")));
        assertEquals("2026-2027",
            AnneeScolaireParents.choisir("annee-invalide", List.of("2026-2027", "n'importe quoi")));
    }

    @Test
    void sans_aucune_information_il_n_y_a_pas_de_filtre() {
        assertNull(AnneeScolaireParents.choisir(null, List.of()));
        assertNull(AnneeScolaireParents.choisir("", null));
    }
}