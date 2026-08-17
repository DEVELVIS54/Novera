package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.ResultatPassage;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import com.fasodev.gestionscolaire.repositories.ResultatPassageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de gestion du passage en classe supérieure.
 * 
 * Logique de calcul :
 * ─────────────────────────────────────────────────────────────
 * Moyenne annuelle = (Moy T1 + Moy T2 + Moy T3) / 3
 * 
 * Statut calculé automatiquement :
 *   1. Si classe d'examen national → logique spéciale (voir module Examen National)
 *   2. Si Moy >= seuil_admission (ex: 10/20) → ADMIS (passe automatique)
 *   3. Si Moy < seuil_redoublement (ex: 8/20) → REDOUBLE (redouble direct)
 *   4. Si entre les deux → DELIBERATION (conseil de classe)
 * 
 * Statut final = décision du Directeur après délibération (si DELIBERATION)
 * ─────────────────────────────────────────────────────────────
 */
public class PassageClasseService {

    /**
     * Calcule le statut de passage pour un étudiant donné.
     * Récupère ses moyennes (T1, T2, T3) et applique la logique des 2 seuils.
     * 
     * @param etudiant l'étudiant
     * @param moyenneT1 moyenne trimestre 1
     * @param moyenneT2 moyenne trimestre 2
     * @param moyenneT3 moyenne trimestre 3
     * @param annee_scolaire année scolaire (ex: "2024-2025")
     * @return ResultatPassage avec statut calculé
     */
    public static ResultatPassage calculerStatut(Etudiant etudiant, double moyenneT1, double moyenneT2, 
                                                   double moyenneT3, String annee_scolaire) {
        // Vérifier si la classe est un examen national (logique spéciale)
        Classe classe = ClasseRepository.obtenirParId(etudiant.getClasseId());
        if (classe != null && classe.isEstClasseExamen()) {
            // Les classes d'examen national ne calculent pas le passage automatiquement
            // Voir module ResultatsExamenNational pour la logique spécifique
            return null;
        }

        // Récupérer la configuration des seuils
        ConfigurationEcoleRepository.ConfigEcole config = ConfigurationEcoleRepository.obtenirConfiguration();
        double seuilAdmission = config.seuilAdmission;
        double seuilRedoublement = config.seuilRedoublement;

        // Calculer moyenne annuelle
        double moyenneAnnuelle = (moyenneT1 + moyenneT2 + moyenneT3) / 3.0;

        // Déterminer le statut
        String statut;
        if (moyenneAnnuelle >= seuilAdmission) {
            statut = "ADMIS";
        } else if (moyenneAnnuelle < seuilRedoublement) {
            statut = "REDOUBLE";
        } else {
            statut = "DELIBERATION";
        }

        // Créer le résultat
        ResultatPassage resultat = new ResultatPassage(
            etudiant.getId(),
            etudiant.getNomComplet(),
            etudiant.getClasseNom(),
            annee_scolaire
        );
        resultat.setMoyenneT1(moyenneT1);
        resultat.setMoyenneT2(moyenneT2);
        resultat.setMoyenneT3(moyenneT3);
        resultat.setMoyenneAnnuelle(moyenneAnnuelle);
        resultat.setStatutCalcule(statut);
        resultat.setClasseOriginId(etudiant.getClasseId());

        return resultat;
    }

    /**
     * Calcule les passages pour TOUS les étudiants actifs d'une classe.
     * 
     * @param classeId ID de la classe
     * @param annee_scolaire année scolaire
     * @return liste des ResultatPassage calculés
     */
    public static List<ResultatPassage> calculerPourClasse(int classeId, String annee_scolaire) {
        List<ResultatPassage> resultats = new ArrayList<>();

        // Récupérer tous les étudiants actifs de la classe
        List<Etudiant> etudiants = EtudiantRepository.obtenirParClasse(classeId);

        for (Etudiant etudiant : etudiants) {
            if (!etudiant.isActif()) {
                continue;
            }

            // Récupérer les moyennes trimestrielles
            double moyT1 = CalculMoyenneTrimestreService.obtenirMoyenneEtudiant(
                etudiant.getId(), 1, annee_scolaire
            );
            double moyT2 = CalculMoyenneTrimestreService.obtenirMoyenneEtudiant(
                etudiant.getId(), 2, annee_scolaire
            );
            double moyT3 = CalculMoyenneTrimestreService.obtenirMoyenneEtudiant(
                etudiant.getId(), 3, annee_scolaire
            );

            // Calculer le statut
            ResultatPassage resultat = calculerStatut(etudiant, moyT1, moyT2, moyT3, annee_scolaire);
            if (resultat != null) {
                resultats.add(resultat);
            }
        }

        return resultats;
    }

    /**
     * Enregistre en base les résultats de passage calculés.
     * Crée les enregistrements dans la table resultats_annuels.
     * 
     * @param resultats liste des ResultatPassage à enregistrer
     */
    public static void enregistrerResultats(List<ResultatPassage> resultats) {
        for (ResultatPassage resultat : resultats) {
            if (!ResultatPassageRepository.existe(resultat.getEtudiantId(), resultat.getAnnee_scolaire())) {
                ResultatPassageRepository.creer(resultat);
            }
        }
    }

    /**
     * Valide un passage en délibération (décision du Directeur).
     * Change le statut final et enregistre qui a validé et quand.
     * 
     * @param resultatId ID du résultat à valider
     * @param nouveauStatut ADMIS ou REDOUBLE
     * @param valideePar nom de la personne qui valide (ex: "Directeur")
     */
    public static void validerDeliberation(int resultatId, String nouveauStatut, String valideePar) {
        ResultatPassage resultat = ResultatPassageRepository.obtenirParId(resultatId);

        if (resultat == null) {
            throw new IllegalArgumentException("Résultat introuvable : " + resultatId);
        }

        if (!resultat.getStatutCalcule().equals("DELIBERATION")) {
            throw new IllegalStateException(
                "Ce résultat n'est pas en délibération. Statut calculé : " + resultat.getStatutCalcule()
            );
        }

        if (!nouveauStatut.equals("ADMIS") && !nouveauStatut.equals("REDOUBLE")) {
            throw new IllegalArgumentException("Statut invalide : " + nouveauStatut + 
                ". Doit être ADMIS ou REDOUBLE");
        }

        // Mettre à jour
        resultat.setStatutFinal(nouveauStatut);
        resultat.setValideePar(valideePar);
        resultat.setDateValidation(LocalDateTime.now());

        ResultatPassageRepository.mettreAJour(resultat);
    }

    /**
     * Applique les passages validés (change le statut de l'étudiant et sa classe).
     * 
     * Logique :
     *   - ADMIS → classe suivante (prochaine classe logique)
     *   - REDOUBLE → reste dans sa classe actuelle
     * 
     * @param resultat le résultat validé
     */
    public static void appliquerPassage(ResultatPassage resultat) {
        Etudiant etudiant = EtudiantRepository.obtenirParId(resultat.getEtudiantId());

        if (etudiant == null) {
            throw new IllegalArgumentException("Étudiant introuvable : " + resultat.getEtudiantId());
        }

        if (resultat.getStatutFinal() == null) {
            throw new IllegalStateException("Le statut final n'a pas été validé");
        }

        if (resultat.getStatutFinal().equals("ADMIS")) {
            // Déterminer la classe suivante
            Classe classeActuelle = ClasseRepository.obtenirParId(etudiant.getClasseId());
            int classeIdSuivante = determinerClasseSuivante(classeActuelle);

            if (classeIdSuivante <= 0) {
                // Fin de parcours (dernière classe)
                etudiant.setStatutScolarite("sorti");
            } else {
                etudiant.setClasseId(classeIdSuivante);
                Classe classeSuivante = ClasseRepository.obtenirParId(classeIdSuivante);
                etudiant.setClasseNom(classeSuivante.getNom());
            }

            resultat.setClasseSuivanteId(classeIdSuivante);

        } else if (resultat.getStatutFinal().equals("REDOUBLE")) {
            // Reste dans sa classe, incrémenter redoublements
            etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
            resultat.setClasseSuivanteId(etudiant.getClasseId());
        }

        // Sauvegarder l'étudiant modifié
        EtudiantRepository.mettreAJour(etudiant);

        // Sauvegarder le résultat avec la classe suivante
        ResultatPassageRepository.mettreAJour(resultat);
    }

    /**
     * Détermine la classe suivante après admission.
     * Logique simple : parcourt toutes les classes et retourne la "prochaine" en ordre hiérarchique.
     * 
     * À améliorer : intégrer une table de succession de classes (6A → 5A, 3A → 2nde, etc.)
     * 
     * @param classeActuelle la classe actuelle
     * @return ID de la classe suivante, ou 0 si fin de parcours
     */
    private static int determinerClasseSuivante(Classe classeActuelle) {
        // Pour l'instant, logique simple basée sur le niveau
        // À améliorer avec une vraie table de succession
        String niveau = classeActuelle.getNiveau();

        // Exemples (À adapter selon votre structure)
        return switch (niveau) {
            case "6eme" -> trouverClassePar("5eme");
            case "5eme" -> trouverClassePar("4eme");
            case "4eme" -> trouverClassePar("3eme");
            case "3eme" -> trouverClassePar("2nde");
            case "2nde" -> trouverClassePar("1ere");
            case "1ere" -> trouverClassePar("terminale");
            default -> 0; // Fin de parcours
        };
    }

    /**
     * Cherche une classe par son niveau
     */
    private static int trouverClassePar(String niveau) {
        List<Classe> classes = ClasseRepository.obtenirTous();
        for (Classe c : classes) {
            if (niveau.equalsIgnoreCase(c.getNiveau())) {
                return c.getId();
            }
        }
        return 0;
    }

    /**
     * Récupère la configuration actuelle des seuils
     */
    public static ConfigurationEcoleRepository.ConfigEcole obtenirSeuils() {
        return ConfigurationEcoleRepository.obtenirConfiguration();
    }

    /**
     * Met à jour les seuils d'admission et redoublement
     */
    public static void mettreAJourSeuils(double seuilAdmission, double seuilRedoublement) {
        ConfigurationEcoleRepository.mettreAJourSeuils(seuilAdmission, seuilRedoublement);
    }
}
