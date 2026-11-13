package com.fasodev.novera.server.parents;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Choix de l'année scolaire affichée aux parents. */
public final class AnneeScolaireParents {

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-(\\d{4})");

    private AnneeScolaireParents() {
    }

    /** Vrai pour « AAAA-AAAA » avec deux années consécutives (ex. 2026-2027). */
    public static boolean estValide(String annee) {
        if (annee == null) {
            return false;
        }
        Matcher m = FORMAT.matcher(annee);
        return m.matches() && Integer.parseInt(m.group(2)) == Integer.parseInt(m.group(1)) + 1;
    }

    /**
     * Année à afficher : l'année active de l'école si elle est valide, sinon la plus récente
     * trouvée dans les données de l'élève, sinon {@code null} (aucun filtre).
     */
    public static String choisir(String anneeActiveConfiguree, Collection<String> anneesPresentes) {
        if (estValide(anneeActiveConfiguree)) {
            return anneeActiveConfiguree;
        }
        String plusRecente = null;
        if (anneesPresentes != null) {
            for (String annee : anneesPresentes) {
                if (estValide(annee) && (plusRecente == null || annee.compareTo(plusRecente) > 0)) {
                    plusRecente = annee;
                }
            }
        }
        return plusRecente;
    }
}