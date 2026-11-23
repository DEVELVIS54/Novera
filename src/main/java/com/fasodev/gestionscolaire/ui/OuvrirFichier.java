package com.fasodev.gestionscolaire.ui;

import java.awt.Desktop;
import java.io.File;

/**
 * Ouvre un fichier (PDF généré) avec l'application par défaut du système, comme le fait déjà
 * l'écran des paiements pour les reçus. Ne bloque jamais : si l'ouverture est impossible
 * (pas d'environnement graphique, fichier absent, aucune visionneuse), le fichier reste créé.
 */
public final class OuvrirFichier {

    private OuvrirFichier() {
    }

    /** Ouvre le fichier ; ne fait rien (sans erreur) s'il est nul, absent ou si le système ne le permet pas. */
    public static void ouvrir(File fichier) {
        if (fichier == null || !fichier.exists()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(fichier);
            }
        } catch (Exception e) {
            System.out.println("Impossible d'ouvrir automatiquement le fichier : " + e.getMessage());
        }
    }
}