package com.fasodev.gestionscolaire.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnneeScolaireTest {

    @Test
    void l_annee_scolaire_commence_en_septembre() {
        assertEquals("2026-2027", AnneeScolaire.deLaDate(LocalDate.of(2026, 11, 7)));
        assertEquals("2026-2027", AnneeScolaire.deLaDate(LocalDate.of(2026, 9, 1)));
        assertEquals("2025-2026", AnneeScolaire.deLaDate(LocalDate.of(2026, 8, 31)));
        assertEquals("2026-2027", AnneeScolaire.deLaDate(LocalDate.of(2027, 3, 10)));
    }

    @Test
    void le_format_doit_etre_deux_annees_consecutives() {
        assertTrue(AnneeScolaire.estValide("2026-2027"));
        assertFalse(AnneeScolaire.estValide("2026-2028"));
        assertFalse(AnneeScolaire.estValide("26-27"));
        assertFalse(AnneeScolaire.estValide("2026/2027"));
        assertFalse(AnneeScolaire.estValide(""));
        assertFalse(AnneeScolaire.estValide(null));
    }

    @Test
    void annee_suivante_et_precedente() {
        assertEquals("2027-2028", AnneeScolaire.suivante("2026-2027"));
        assertEquals("2025-2026", AnneeScolaire.precedente("2026-2027"));
    }

    @Test
    void une_annee_invalide_est_refusee() {
        assertThrows(IllegalArgumentException.class, () -> AnneeScolaire.suivante("2026"));
        assertThrows(IllegalArgumentException.class, () -> AnneeScolaire.precedente(null));
    }
}