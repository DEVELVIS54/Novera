package com.fasodev.gestionscolaire.utils;

import java.time.LocalDate;
import java.time.Month;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outils sur l'année scolaire, écrite « 2026-2027 » (deux années consécutives).
 * L'année scolaire commence en septembre : le 7 novembre 2026 est dans « 2026-2027 »,
 * le 10 mars 2027 aussi.
 */
public final class AnneeScolaire {

    /** Mois à partir duquel une nouvelle année scolaire commence (septembre). */
    public static final int MOIS_RENTREE = Month.SEPTEMBER.getValue();

    private static final Pattern FORMAT = Pattern.compile("(\\d{4})-(\\d{4})");

    private AnneeScolaire() {
    }

    /** Année scolaire à laquelle appartient une date. */
    public static String deLaDate(LocalDate date) {
        int debut = date.getMonthValue() >= MOIS_RENTREE ? date.getYear() : date.getYear() - 1;
        return debut + "-" + (debut + 1);
    }

    /** Année scolaire d'aujourd'hui. */
    public static String courante() {
        return deLaDate(LocalDate.now());
    }

    /** Vrai si le texte a la forme « AAAA-AAAA » avec deux années consécutives. */
    public static boolean estValide(String annee) {
        if (annee == null) {
            return false;
        }
        Matcher m = FORMAT.matcher(annee);
        return m.matches() && Integer.parseInt(m.group(2)) == Integer.parseInt(m.group(1)) + 1;
    }

    /** Année qui suit (« 2026-2027 » donne « 2027-2028 »). */
    public static String suivante(String annee) {
        int debut = debut(annee);
        return (debut + 1) + "-" + (debut + 2);
    }

    /** Année qui précède (« 2026-2027 » donne « 2025-2026 »). */
    public static String precedente(String annee) {
        int debut = debut(annee);
        return (debut - 1) + "-" + debut;
    }

    private static int debut(String annee) {
        if (!estValide(annee)) {
            throw new IllegalArgumentException("Année scolaire invalide : " + annee);
        }
        return Integer.parseInt(annee.substring(0, 4));
    }
}