package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.repositories.ConfigurationSubventionEtatRepository;

public class SubventionEtatService {

    private final ConfigurationSubventionEtatRepository configRepository =
        new ConfigurationSubventionEtatRepository();

    /**
     * Calcule le pourcentage de réduction applicable à un étudiant,
     * selon son palier de subvention (CEP ou BEPC), et seulement
     * si sa subvention est encore active (pas perdue via redoublements).
     * Retourne 0 si l'étudiant n'est pas concerné.
     */
    public double getPourcentageReduction(Etudiant etudiant) {

        if (!etudiant.isAffecteEtat() || !etudiant.isSubventionActive()) {
            return 0;
        }

        ConfigurationSubventionEtatRepository.Config config = configRepository.getConfiguration();

        if ("CEP".equals(etudiant.getPalierSubvention())) {
            return config.reductionPalierCep;
        } else if ("BEPC".equals(etudiant.getPalierSubvention())) {
            return config.reductionPalierBepc;
        }

        return 0;
    }

    /**
     * Applique la réduction (si applicable) sur un montant de frais normal.
     */
    public double calculerFraisReels(Etudiant etudiant, double fraisNormal) {
        double pourcentage = getPourcentageReduction(etudiant);
        return fraisNormal * (1 - (pourcentage / 100.0));
    }

    public ConfigurationSubventionEtatRepository.Config getConfiguration() {
        return configRepository.getConfiguration();
    }

    public void mettreAJourConfiguration(double reductionCep, double reductionBepc, int seuilRedoublements) {

        if (reductionCep < 0 || reductionCep > 100 || reductionBepc < 0 || reductionBepc > 100) {
            throw new IllegalArgumentException("Les pourcentages doivent être entre 0 et 100.");
        }
        if (seuilRedoublements < 1) {
            throw new IllegalArgumentException("Le seuil de redoublements doit être au moins 1.");
        }

        configRepository.mettreAJour(reductionCep, reductionBepc, seuilRedoublements);
    }
}
