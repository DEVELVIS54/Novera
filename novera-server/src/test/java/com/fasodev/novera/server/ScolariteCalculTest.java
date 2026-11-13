package com.fasodev.novera.server;

import com.fasodev.novera.server.parents.ScolariteCalcul;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScolariteCalculTest {

    @Test
    void un_eleve_non_affecte_n_a_pas_de_reduction() {
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(false, true, "CEP", 50, 30));
    }

    @Test
    void une_subvention_perdue_n_donne_plus_de_reduction() {
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(true, false, "BEPC", 50, 30));
    }

    @Test
    void la_reduction_depend_du_palier() {
        assertEquals(50.0, ScolariteCalcul.pourcentageReduction(true, true, "CEP", 50, 30));
        assertEquals(30.0, ScolariteCalcul.pourcentageReduction(true, true, "BEPC", 50, 30));
    }

    @Test
    void un_palier_absent_ou_inconnu_ne_donne_pas_de_reduction() {
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(true, true, null, 50, 30));
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(true, true, "", 50, 30));
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(true, true, "BAC", 50, 30));
    }

    @Test
    void le_pourcentage_reste_entre_0_et_100() {
        assertEquals(100.0, ScolariteCalcul.pourcentageReduction(true, true, "CEP", 250, 0));
        assertEquals(0.0, ScolariteCalcul.pourcentageReduction(true, true, "CEP", -10, 0));
    }

    @Test
    void les_frais_dus_appliquent_la_reduction() {
        assertEquals(50000.0, ScolariteCalcul.fraisDus(100000, 50));
        assertEquals(100000.0, ScolariteCalcul.fraisDus(100000, 0));
        assertEquals(0.0, ScolariteCalcul.fraisDus(100000, 100));
    }

    @Test
    void le_reste_a_payer_n_est_jamais_negatif() {
        assertEquals(30000.0, ScolariteCalcul.resteAPayer(50000, 20000));
        assertEquals(0.0, ScolariteCalcul.resteAPayer(50000, 50000));
        assertEquals(0.0, ScolariteCalcul.resteAPayer(50000, 70000));
    }
}