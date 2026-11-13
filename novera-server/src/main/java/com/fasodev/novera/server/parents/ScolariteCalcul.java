package com.fasodev.novera.server.parents;

/**
 * Calcul des frais de scolarité d'un élève pour l'espace parents. Reprend la règle du logiciel
 * de bureau : un élève affecté par l'État, dont la subvention est encore active, bénéficie d'une
 * réduction selon son palier (CEP ou BEPC) ; frais dus = frais normaux × (1 − réduction / 100).
 */
public final class ScolariteCalcul {

    private ScolariteCalcul() {
    }

    /** Pourcentage de réduction applicable (0 si l'élève n'est pas concerné). */
    public static double pourcentageReduction(boolean affecteEtat, boolean subventionActive, String palier,
                                              double reductionCep, double reductionBepc) {
        if (!affecteEtat || !subventionActive) {
            return 0;
        }
        double pourcentage = switch (palier == null ? "" : palier) {
            case "CEP" -> reductionCep;
            case "BEPC" -> reductionBepc;
            default -> 0;
        };
        return Math.max(0, Math.min(100, pourcentage));
    }

    /** Frais dus après réduction, arrondis au centime. */
    public static double fraisDus(double fraisNormaux, double pourcentageReduction) {
        return arrondir(fraisNormaux * (1 - pourcentageReduction / 100.0));
    }

    /** Reste à payer (jamais négatif : un trop-perçu n'est pas affiché comme une dette). */
    public static double resteAPayer(double fraisDus, double totalPaye) {
        return arrondir(Math.max(0, fraisDus - totalPaye));
    }

    private static double arrondir(double valeur) {
        return Math.round(valeur * 100.0) / 100.0;
    }
}