package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Reinscription;
import com.fasodev.gestionscolaire.models.ResultatExamenNational;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import com.fasodev.gestionscolaire.repositories.ReinscriptionRepository;
import com.fasodev.gestionscolaire.repositories.ResultatExamenNationalRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de gestion des examens nationaux (CEP, BEPC, BAC).
 *
 * Logique :
 * ────────────────────────────────────────────────────────────────
 * 1. Saisir résultat examen (Secrétaire) :
 *    - ADMIS → Élève passe, peut se réinscrire
 *    - REFUSÉ + Redouble → Redouble la classe
 *    - REFUSÉ + Parti → Élève quitte l'école
 *
 * 2. Réinscription (après admission) :
 *    - Si classe = lycée (2nde/1ère) → Parent confirme nouvelle classe
 *    - Si classe = Terminale (fin de parcours) → Pas de réinscription
 *      (sort automatiquement "Sorti - Poursuite université")
 *
 * 3. Application des résultats :
 *    - Si Terminale + Admis → Sorti (fin de parcours)
 *    - Si Terminale + Refusé → Redouble (rare) ou Parti
 *    - Si CEP/BEPC + Admis → Enattente réinscription
 *    - Si CEP/BEPC + Refusé → Redouble ou Parti
 * ────────────────────────────────────────────────────────────────
 */
public class ExamenNationalService {

    /**
     * Crée les dossiers d'examen pour tous les étudiants d'une classe examen
     */
    public static List<ResultatExamenNational> creerDossiersPour(int classeId, String annee) {
        List<ResultatExamenNational> dossiers = new ArrayList<>();

        Classe classe = ClasseRepository.obtenirParId(classeId);
        if (classe == null || !classe.isEstClasseExamen()) {
            return dossiers; // Classe pas d'examen
        }

        List<Etudiant> etudiants = EtudiantRepository.obtenirParClasse(classeId);

        for (Etudiant etudiant : etudiants) {
            if (!etudiant.isActif()) {
                continue;
            }

            // Vérifier s'il y a déjà un dossier
            if (!ResultatExamenNationalRepository.existe(etudiant.getId(), annee)) {
                ResultatExamenNational dossier = new ResultatExamenNational(
                    etudiant.getId(),
                    etudiant.getNomComplet(),
                    classeId,
                    classe.getNom(),
                    annee,
                    classe.getNomExamen()
                );
                ResultatExamenNationalRepository.creer(dossier);
                dossiers.add(dossier);
            }
        }

        return dossiers;
    }

    /**
     * Enregistre le résultat de l'examen (saisie Secrétaire)
     *
     * @param resultatId      ID du résultat
     * @param resultat        "admis" ou "refuse"
     * @param decision_refuse "redouble" ou "parti" (ignoré si admis)
     * @param saisir_par      Nom de la Secrétaire
     */
    public static void enregistrerResultat(int resultatId, String resultat, 
                                           String decision_refuse, String saisir_par) {
        ResultatExamenNational resultat_obj = ResultatExamenNationalRepository.obtenirParId(resultatId);

        if (resultat_obj == null) {
            throw new IllegalArgumentException("Résultat introuvable : " + resultatId);
        }

        if (!resultat.equals("admis") && !resultat.equals("refuse")) {
            throw new IllegalArgumentException("Résultat invalide : " + resultat);
        }

        if (resultat.equals("refuse")) {
            if (!decision_refuse.equals("redouble") && !decision_refuse.equals("parti")) {
                throw new IllegalArgumentException("Décision invalide : " + decision_refuse);
            }
            resultat_obj.setDecision_si_refuse(decision_refuse);
        }

        resultat_obj.setResultat(resultat);
        resultat_obj.setSaisi_par(saisir_par);
        resultat_obj.setDate_saisie(LocalDateTime.now());

        ResultatExamenNationalRepository.mettreAJour(resultat_obj);
    }

    /**
     * Crée automatiquement les demandes de réinscription après résultats positifs
     *
     * Pour classes non-Terminale : crée demande réinscription en_attente
     * Pour Terminale : marque élève comme "Sorti"
     */
    public static void creerReinscriptionsApres(List<ResultatExamenNational> resultats) {
        for (ResultatExamenNational resultat : resultats) {
            if (!resultat.getResultat().equals("admis")) {
                continue; // Seuls les admis se réinscrivent
            }

            Classe classe = ClasseRepository.obtenirParId(resultat.getClasseId());
            if (classe == null) {
                continue;
            }

            Etudiant etudiant = EtudiantRepository.obtenirParId(resultat.getEtudiantId());

            if (classe.isFinDeParcours()) {
                // Terminale → Sorti (fin de parcours, pas de réinscription)
                etudiant.setStatutScolarite("sorti");
                etudiant.setCommentaireStatut("Sorti - Poursuite université");
                EtudiantRepository.mettreAJour(etudiant);

                resultat.setStatut_final("SORTI_FIN_PARCOURS");
                ResultatExamenNationalRepository.mettreAJour(resultat);
            } else {
                // CEP/BEPC → Créer demande réinscription
                Classe classeSuivante = determinerClasseSuivante(classe);
                if (classeSuivante != null) {
                    Reinscription reinscription = new Reinscription(
                        etudiant.getId(),
                        etudiant.getNomComplet(),
                        resultat.getId(),
                        resultat.getAnnee_scolaire()
                    );
                    reinscription.setNouvelle_classe_id(classeSuivante.getId());
                    reinscription.setNouvelle_classe_nom(classeSuivante.getNom());

                    ReinscriptionRepository.creer(reinscription);

                    // Marquer étudiant en attente réinscription
                    etudiant.setStatutScolarite("en_attente_reinscription");
                    EtudiantRepository.mettreAJour(etudiant);

                    resultat.setStatut_final("EN_ATTENTE_REINSCRIPTION");
                    ResultatExamenNationalRepository.mettreAJour(resultat);
                }
            }
        }
    }

    /**
     * Applique les décisions finales pour les refusés (redouble ou parti)
     */
    public static void appliquerDecisionsRefuses(List<ResultatExamenNational> resultats) {
        for (ResultatExamenNational resultat : resultats) {
            if (!resultat.getResultat().equals("refuse")) {
                continue;
            }

            Etudiant etudiant = EtudiantRepository.obtenirParId(resultat.getEtudiantId());
            if (etudiant == null) {
                continue;
            }

            String decision = resultat.getDecision_si_refuse();

            if (decision.equals("redouble")) {
                // Redouble : reste dans sa classe, incrémenter compteur
                etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
                resultat.setStatut_final("REDOUBLE");
            } else if (decision.equals("parti")) {
                // Parti : quitte l'école
                etudiant.setStatutScolarite("parti");
                etudiant.setCommentaireStatut("Départ après échec examen");
                resultat.setStatut_final("PARTI");
            }

            EtudiantRepository.mettreAJour(etudiant);
            ResultatExamenNationalRepository.mettreAJour(resultat);
        }
    }

    /**
     * Détermine la classe suivante (logique simple, à améliorer avec une vraie table de succession)
     */
    private static Classe determinerClasseSuivante(Classe classeActuelle) {
        String niveau = classeActuelle.getNiveau();

        // Parcours type : 6ème → 5ème → 4ème → 3ème (CEP) → 2nde → 1ère → Terminale (BAC)
        String niveauSuivant = switch (niveau) {
            case "6eme" -> "5eme";
            case "5eme" -> "4eme";
            case "4eme" -> "3eme";
            case "3eme" -> "2nde";  // Après CEP
            case "2nde" -> "1ere";
            case "1ere" -> "terminale";
            default -> null;
        };

        if (niveauSuivant != null) {
            List<Classe> classes = ClasseRepository.obtenirTous();
            for (Classe c : classes) {
                if (niveauSuivant.equalsIgnoreCase(c.getNiveau())) {
                    return c;
                }
            }
        }

        return null;
    }

    /**
     * Récupère les résultats non finalisés (attendant décision Secrétaire)
     */
    public static List<ResultatExamenNational> obtenirNonFinalisés(String annee) {
        return ResultatExamenNationalRepository.obtenirNonFinalisés(annee);
    }

    /**
     * Récupère les résultats d'une classe
     */
    public static List<ResultatExamenNational> obtenirParClasse(int classeId, String annee) {
        return ResultatExamenNationalRepository.obtenirParClasse(classeId, annee);
    }
}
