package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Reinscription;
import com.fasodev.gestionscolaire.models.ResultatExamenNational;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import com.fasodev.gestionscolaire.repositories.ReinscriptionRepository;
import com.fasodev.gestionscolaire.repositories.ResultatExamenNationalRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de gestion des réinscriptions après admissions.
 *
 * Flux réinscription :
 * ────────────────────────────────────────────────────────────────
 * 1. Élève admis examen CEP/BEPC → Demande réinscription créée
 * 2. Parent notifié (futur portail) → Doit confirmer
 * 3. Parent confirme → Statut = "confirmee", classe mise à jour
 * 4. Délai expiré (30 jours?) → Élève considéré "parti"
 * ────────────────────────────────────────────────────────────────
 */
public class ReinscriptionService {

    /**
     * Confirme une réinscription (parent accepte)
     *
     * Mises à jour :
     *   1. Reinscription.statut = "confirmee"
     *   2. Étudiant.classe_id = nouvelle classe
     *   3. Étudiant.statut_scolarite = "actif"
     *   4. Résultat examen.statut_final = "RÉINSCRIT"
     *
     * @param reinscriptionId ID de la demande réinscription
     * @param confirmee_par   Email/nom du parent qui confirme
     */
    public static void confirmerReinscription(int reinscriptionId, String confirmee_par) {
        Reinscription reinscription = ReinscriptionRepository.obtenirParId(reinscriptionId);

        if (reinscription == null) {
            throw new IllegalArgumentException("Réinscription introuvable : " + reinscriptionId);
        }

        if (reinscription.isConfirmee()) {
            throw new IllegalStateException("Réinscription déjà confirmée");
        }

        // Mettre à jour la réinscription
        reinscription.setConfirmee_par(confirmee_par);
        reinscription.setDate_confirmation(LocalDateTime.now());
        reinscription.setStatut("confirmee");
        ReinscriptionRepository.confirmer(reinscription);

        // Mettre à jour l'étudiant (changement classe + statut)
        Etudiant etudiant = EtudiantRepository.obtenirParId(reinscription.getEtudiantId());
        if (etudiant != null) {
            etudiant.setClasseId(reinscription.getNouvelle_classe_id());
            // Récupérer le nom de la classe
            try {
                etudiant.setClasseNom(reinscription.getNouvelle_classe_nom());
            } catch (Exception ignored) {}
            etudiant.setStatutScolarite("actif");
            EtudiantRepository.mettreAJour(etudiant);
        }

        // Marquer le résultat examen comme réinscrit
        try {
            ResultatExamenNational resultat = ResultatExamenNationalRepository
                .obtenirParId(reinscription.getResultat_examen_id());
            if (resultat != null) {
                resultat.setStatut_final("RÉINSCRIT");
                ResultatExamenNationalRepository.mettreAJour(resultat);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Refuse une réinscription (parent refuse ou délai expiré)
     *
     * Mises à jour :
     *   1. Reinscription.statut = "refusee"
     *   2. Étudiant.statut_scolarite = "parti"
     *   3. Résultat examen.statut_final = "REFUSÉ_RÉINSCRIPTION"
     *
     * @param reinscriptionId ID de la demande
     * @param raison          Raison du refus (optionnel)
     */
    public static void refuserReinscription(int reinscriptionId, String raison) {
        Reinscription reinscription = ReinscriptionRepository.obtenirParId(reinscriptionId);

        if (reinscription == null) {
            throw new IllegalArgumentException("Réinscription introuvable : " + reinscriptionId);
        }

        if (reinscription.isConfirmee()) {
            throw new IllegalStateException("Réinscription déjà confirmée, impossible de refuser");
        }

        // Mettre à jour la réinscription
        ReinscriptionRepository.refuser(reinscriptionId);

        // Mettre à jour l'étudiant (parti)
        Etudiant etudiant = EtudiantRepository.obtenirParId(reinscription.getEtudiantId());
        if (etudiant != null) {
            etudiant.setStatutScolarite("parti");
            etudiant.setCommentaireStatut("Refus réinscription : " + (raison != null ? raison : ""));
            EtudiantRepository.mettreAJour(etudiant);
        }

        // Marquer le résultat examen
        try {
            ResultatExamenNational resultat = ResultatExamenNationalRepository
                .obtenirParId(reinscription.getResultat_examen_id());
            if (resultat != null) {
                resultat.setStatut_final("REFUSÉ_RÉINSCRIPTION");
                ResultatExamenNationalRepository.mettreAJour(resultat);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Récupère les réinscriptions en attente
     */
    public static List<Reinscription> obtenirEnAttente(String annee) {
        return ReinscriptionRepository.obtenirEnAttente(annee);
    }

    /**
     * Récupère les réinscriptions confirmées
     */
    public static List<Reinscription> obtenirConfirmees(String annee) {
        return ReinscriptionRepository.obtenirConfirmees(annee);
    }

    /**
     * Obtient la réinscription pour un étudiant
     */
    public static Reinscription obtenirParEtudiant(int etudiantId, String annee) {
        return ReinscriptionRepository.obtenirParEtudiant(etudiantId, annee);
    }

    /**
     * Statistiques des réinscriptions
     */
    public static void afficherStatistiques(String annee) {
        List<Reinscription> enAttente = obtenirEnAttente(annee);
        List<Reinscription> confirmees = obtenirConfirmees(annee);

        long confirmeeCount = confirmees.size();
        long enAttenteCount = enAttente.size();
        long totalCount = confirmeeCount + enAttenteCount;

        if (totalCount > 0) {
            double tauxConfirmation = (confirmeeCount * 100.0) / totalCount;
            System.out.printf(
                "📊 Réinscriptions %s : %d confirmées, %d en attente (%.1f%% confirmées)%n",
                annee, confirmeeCount, enAttenteCount, tauxConfirmation
            );
        }
    }
}
