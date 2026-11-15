package com.fasodev.gestionscolaire;

/**
 * Point d'entree de l'application empaquetee (.exe / jar unique).
 *
 * <p>Pourquoi une classe separee : quand la classe de demarrage etend directement
 * javafx.application.Application, Java verifie la presence des modules JavaFX avant meme de
 * lancer le programme et echoue avec "JavaFX runtime components are missing". Cette classe
 * n'etend rien : elle se contente de lancer {@link Main}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}