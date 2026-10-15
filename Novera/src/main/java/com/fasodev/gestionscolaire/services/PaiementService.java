package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Paiement;
import com.fasodev.gestionscolaire.models.SoldeEtudiant;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import com.fasodev.gestionscolaire.repositories.FraisScolariteRepository;
import com.fasodev.gestionscolaire.repositories.PaiementRepository;

import java.time.LocalDate;
import java.util.List;

public class PaiementService {

    private final PaiementRepository paiementRepository = new PaiementRepository();
    private final FraisScolariteRepository fraisRepository = new FraisScolariteRepository();
    private final EtudiantRepository etudiantRepository = new EtudiantRepository();
    private final SubventionEtatService subventionEtatService = new SubventionEtatService();

    /**
     * Calcule la situation financière complète d'un étudiant :
     * frais normal de sa classe, réduction subvention appliquée
     * automatiquement si applicable, total déjà payé, et solde restant.
     */
    public SoldeEtudiant calculerSolde(int etudiantId) {

        Etudiant etudiant = etudiantRepository.findById(etudiantId);
        if (etudiant == null) {
            throw new IllegalArgumentException("Étudiant introuvable.");
        }

        double fraisNormal = fraisRepository.getMontantTotalParClasse(etudiant.getClasseId());
        double fraisReel = subventionEtatService.calculerFraisReels(etudiant, fraisNormal);
        double totalPaye = paiementRepository.getTotalPayeParEtudiant(etudiantId);

        SoldeEtudiant solde = new SoldeEtudiant();
        solde.setFraisNormal(fraisNormal);
        solde.setFraisReel(fraisReel);
        solde.setTotalPaye(totalPaye);
        solde.setSubventionAppliquee(fraisReel < fraisNormal);
        solde.setPourcentageReduction(subventionEtatService.getPourcentageReduction(etudiant));

        return solde;
    }

    public List<Paiement> historique(int etudiantId) {
        return paiementRepository.findByEtudiantId(etudiantId);
    }

    public Paiement enregistrerPaiement(int etudiantId, double montant, LocalDate date, String notes) {

        if (montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0.");
        }

        SoldeEtudiant soldeAvant = calculerSolde(etudiantId);

        if (montant > soldeAvant.getSolde() && soldeAvant.getSolde() > 0) {
            // Pas bloquant, juste informatif — on autorise le trop-perçu
            // (ex: avance pour un prochain frais), mais on pourrait aussi
            // choisir de l'interdire selon préférence future.
        }

        Paiement paiement = new Paiement();
        paiement.setEtudiantId(etudiantId);
        paiement.setMontant(montant);
        paiement.setDatePaiement(date != null ? date : LocalDate.now());
        paiement.setNotes(notes);
        paiement.setCreePar(SessionManager.getUtilisateurConnecte() != null
            ? SessionManager.getUtilisateurConnecte().getNomUtilisateur() : "inconnu");

        // Statut calculé APRÈS ce paiement
        double totalApres = soldeAvant.getTotalPaye() + montant;
        String statut;
        if (totalApres >= soldeAvant.getFraisReel()) {
            statut = "payé";
        } else if (totalApres > 0) {
            statut = "partiellement";
        } else {
            statut = "impayé";
        }
        paiement.setStatut(statut);

        return paiementRepository.save(paiement);
    }

    public void annulerPaiement(int paiementId) {
        paiementRepository.delete(paiementId);
    }
}
