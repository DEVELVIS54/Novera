package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.repositories.ConfigurationSubventionEtatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Subvention de l'Etat : reduction selon le palier, sur une vraie base temporaire. */
class SubventionEtatServiceTest {

    @TempDir
    Path dossier;

    private SubventionEtatService service;

    @BeforeEach
    void preparer() {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();
        service = new SubventionEtatService();
        service.mettreAJourConfiguration(50, 30, 2);   // CEP : 50 %, BEPC : 30 %, perte apres 2 redoublements
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private Etudiant eleve(boolean affecte, boolean subventionActive, String palier) {
        Etudiant e = new Etudiant();
        e.setAffecteEtat(affecte);
        e.setSubventionActive(subventionActive);
        e.setPalierSubvention(palier);
        return e;
    }

    @Test
    void un_eleve_non_affecte_n_a_pas_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(false, true, "CEP")));
    }

    @Test
    void une_subvention_perdue_ne_donne_plus_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, false, "BEPC")));
    }

    @Test
    void la_reduction_depend_du_palier() {
        assertEquals(50.0, service.getPourcentageReduction(eleve(true, true, "CEP")));
        assertEquals(30.0, service.getPourcentageReduction(eleve(true, true, "BEPC")));
    }

    @Test
    void un_palier_absent_ou_inconnu_ne_donne_pas_de_reduction() {
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, true, null)));
        assertEquals(0.0, service.getPourcentageReduction(eleve(true, true, "BAC")));
    }

    @Test
    void les_frais_reels_appliquent_la_reduction() {
        assertEquals(50000.0, service.calculerFraisReels(eleve(true, true, "CEP"), 100000), 0.001);
        assertEquals(100000.0, service.calculerFraisReels(eleve(false, true, "CEP"), 100000), 0.001);
    }

    @Test
    void la_configuration_refuse_les_valeurs_invalides() {
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(-1, 30, 2));
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(50, 101, 2));
        assertThrows(IllegalArgumentException.class, () -> service.mettreAJourConfiguration(50, 30, 0));
    }

    @Test
    void la_configuration_est_enregistree() {
        service.mettreAJourConfiguration(40, 20, 3);

        ConfigurationSubventionEtatRepository.Config config = service.getConfiguration();

        assertEquals(40.0, config.reductionPalierCep);
        assertEquals(20.0, config.reductionPalierBepc);
        assertEquals(3, config.seuilRedoublementsPerte);
    }
}