package com.fasodev.gestionscolaire.services;

/** Évalue la solidité d'un mot de passe pour l'indicateur de l'écran de première configuration. */
public final class ForceMotDePasse {

    public static final int AUCUNE = 0;
    public static final int FAIBLE = 1;
    public static final int MOYENNE = 2;
    public static final int FORTE = 3;

    private ForceMotDePasse() {}

    /**
     * 0 = champ vide, 1 = faible, 2 = moyenne, 3 = forte.
     * Forte : au moins 10 caractères et 3 types parmi minuscules, majuscules, chiffres, symboles.
     * Moyenne : au moins 8 caractères et 2 types.
     */
    public static int evaluer(String motDePasse) {
        if (motDePasse == null || motDePasse.isEmpty()) return AUCUNE;

        boolean minuscule = false, majuscule = false, chiffre = false, autre = false;
        for (char c : motDePasse.toCharArray()) {
            if (Character.isLowerCase(c)) minuscule = true;
            else if (Character.isUpperCase(c)) majuscule = true;
            else if (Character.isDigit(c)) chiffre = true;
            else autre = true;
        }
        int types = (minuscule ? 1 : 0) + (majuscule ? 1 : 0) + (chiffre ? 1 : 0) + (autre ? 1 : 0);
        int longueur = motDePasse.length();

        if (longueur >= 10 && types >= 3) return FORTE;
        if (longueur >= 8 && types >= 2) return MOYENNE;
        return FAIBLE;
    }

    public static String libelle(int force) {
        return switch (force) {
            case FAIBLE -> "Faible";
            case MOYENNE -> "Moyen";
            case FORTE -> "Fort";
            default -> "";
        };
    }
}