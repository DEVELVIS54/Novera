package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.services.CalculMoyenneTrimestreService;
import com.fasodev.gestionscolaire.utils.AnneeScolaire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les écrans Passage en classe, Examen national et Réinscriptions travaillent sur une année choisie :
 * la moyenne lue doit être celle de CETTE année, pas celle de l'année active.
 */
class MoyenneParAnneeIntegrationTest {

    @TempDir
    Path dossier;

    private CalculMoyenneRepository calculs;
    private String anneeA;
    private String anneeB;

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('6eme A', '6eme')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Awa', 'Traore', 1)");
        }

        calculs = new CalculMoyenneRepository();
        anneeA = ConfigurationEcoleRepository.obtenirAnneeActive();
        anneeB = AnneeScolaire.suivante(anneeA);

        enregistrerMoyenne(14.0);                                   // année A : 14
        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        enregistrerMoyenne(8.0);                                    // année B : 8 (année active ensuite)
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    /** Enregistre la moyenne du trimestre 1 de l'élève dans l'année active du moment. */
    private void enregistrerMoyenne(double moyenne) {
        int calculId = calculs.obtenirOuCreerCalculId(1, 1);
        MoyenneResultat r = new MoyenneResultat(1, "Awa Traore", moyenne);
        r.setRang(1);
        calculs.sauvegarderMoyennes(calculId, 1, 1, List.of(r));
    }

    @Test
    void une_moyenne_se_lit_pour_l_annee_demandee_quelle_que_soit_l_annee_active() {
        // L'année active est B.
        assertEquals(14.0, calculs.getMoyenneEtudiant(1, 1, anneeA));
        assertEquals(8.0, calculs.getMoyenneEtudiant(1, 1, anneeB));
        assertEquals(8.0, calculs.getMoyenneEtudiant(1, 1));        // sans année : l'année active
    }

    @Test
    void le_service_de_passage_lit_la_moyenne_de_l_annee_choisie() {
        assertEquals(14.0, CalculMoyenneTrimestreService.obtenirMoyenneEtudiant(1, 1, anneeA));
        assertEquals(8.0, CalculMoyenneTrimestreService.obtenirMoyenneEtudiant(1, 1, anneeB));
    }

    @Test
    void les_ecrans_proposent_l_annee_active_et_les_annees_avec_donnees() {
        List<String> proposees = ConfigurationEcoleRepository.anneesDisponibles();

        assertTrue(proposees.contains(anneeA));
        assertTrue(proposees.contains(anneeB));
        assertEquals(anneeB, ConfigurationEcoleRepository.obtenirAnneeActive());
    }
}