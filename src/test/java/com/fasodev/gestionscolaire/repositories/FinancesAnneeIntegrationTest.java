package com.fasodev.gestionscolaire.repositories;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.FraisScolarite;
import com.fasodev.gestionscolaire.models.Paiement;
import com.fasodev.gestionscolaire.utils.AnneeScolaire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration sur une vraie base temporaire (créée par DatabaseInitializer, migrations
 * comprises) : les paiements et les frais sont séparés par année scolaire.
 */
class FinancesAnneeIntegrationTest {

    @TempDir
    Path dossier;

    private PaiementRepository paiements;
    private FraisScolariteRepository frais;
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

        paiements = new PaiementRepository();
        frais = new FraisScolariteRepository();
        anneeA = ConfigurationEcoleRepository.obtenirAnneeActive();
        anneeB = AnneeScolaire.suivante(anneeA);
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private void payer(double montant) {
        Paiement p = new Paiement();
        p.setEtudiantId(1);
        p.setMontant(montant);
        p.setStatut("partiellement");
        paiements.save(p);
    }

    private void ajouterFrais(double montant, String description) {
        FraisScolarite f = new FraisScolarite();
        f.setClasseId(1);
        f.setMontant(montant);
        f.setDescription(description);
        frais.save(f);
    }

    @Test
    void un_paiement_est_compte_dans_l_annee_active() {
        payer(20000);

        assertEquals(20000.0, paiements.getTotalPayeParEtudiant(1));
        assertEquals(1, paiements.findByEtudiantId(1).size());
    }

    @Test
    void changer_d_annee_isole_les_paiements() {
        payer(20000);

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertEquals(0.0, paiements.getTotalPayeParEtudiant(1));
        assertTrue(paiements.findByEtudiantId(1).isEmpty());

        payer(5000);
        assertEquals(5000.0, paiements.getTotalPayeParEtudiant(1));

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals(20000.0, paiements.getTotalPayeParEtudiant(1));
        assertEquals(1, paiements.findByEtudiantId(1).size());
    }

    @Test
    void les_frais_sont_separes_par_annee() {
        ajouterFrais(100000, "Scolarite");

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertEquals(0.0, frais.getMontantTotalParClasse(1));
        assertTrue(frais.findAll().isEmpty());

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals(100000.0, frais.getMontantTotalParClasse(1));
        assertEquals(1, frais.findAll().size());
    }

    @Test
    void les_frais_peuvent_etre_recopies_vers_la_nouvelle_annee_une_seule_fois() {
        ajouterFrais(60000, "Scolarite");
        ajouterFrais(40000, "Cotisation");

        assertEquals(2, frais.copierVersAnnee(anneeA, anneeB));
        assertEquals(0, frais.copierVersAnnee(anneeA, anneeB));   // déjà copiés : rien à refaire

        ConfigurationEcoleRepository.definirAnneeActive(anneeB);
        assertEquals(100000.0, frais.getMontantTotalParClasse(1));

        ConfigurationEcoleRepository.definirAnneeActive(anneeA);
        assertEquals(100000.0, frais.getMontantTotalParClasse(1));   // l'année d'origine est inchangée
    }

    @Test
    void un_paiement_sans_annee_est_refuse_par_la_base() throws SQLException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            assertThrows(SQLException.class,
                () -> st.execute("INSERT INTO paiements (etudiant_id, montant) VALUES (1, 1000)"));
        }
    }
}