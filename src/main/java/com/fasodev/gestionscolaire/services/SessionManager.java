package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Utilisateur;

/**
 * Garde en mémoire l'utilisateur actuellement connecté pendant
 * toute la durée de vie de l'application (session locale, pas de token).
 */
public class SessionManager {

    private static Utilisateur utilisateurConnecte;

    private SessionManager() {
    }

    public static void connecter(Utilisateur utilisateur) {
        utilisateurConnecte = utilisateur;
    }

    public static void deconnecter() {
        utilisateurConnecte = null;
    }

    public static Utilisateur getUtilisateurConnecte() {
        return utilisateurConnecte;
    }

    public static boolean estConnecte() {
        return utilisateurConnecte != null;
    }

    /**
     * Nom à afficher dans l'UI (ex: barre du haut).
     * Utilisé par le reste de l'app SANS jamais manipuler
     * directement le mot de passe ou son hash.
     */
    public static String getNomAffiche() {
        if (utilisateurConnecte == null) return "—";
        String roleLabel = utilisateurConnecte.isAdmin() ? "Directeur" : "Secrétaire";
        return utilisateurConnecte.getNomUtilisateur() + " (" + roleLabel + ")";
    }
}
