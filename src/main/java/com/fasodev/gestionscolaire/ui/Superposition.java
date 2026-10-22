package com.fasodev.gestionscolaire.ui;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Couche transparente qui recouvre toute la fenêtre : accueille la fenêtre volante d'un groupe
 * du dock ou la palette de commandes. Un clic à côté la referme.
 */
public final class Superposition {

    private final StackPane couche;
    private Runnable surFermeture;

    public Superposition(StackPane couche) {
        this.couche = couche;
        couche.setVisible(false);
    }

    public StackPane couche() {
        return couche;
    }

    public boolean estOuverte() {
        return couche.isVisible();
    }

    /** Affiche un contenu ; assombrir = voile sombre derrière (palette), sinon fond transparent. */
    public void afficher(Node contenu, boolean assombrir, Runnable aLaFermeture) {
        fermer();
        surFermeture = aLaFermeture;

        Region fond = new Region();
        fond.getStyleClass().add(assombrir ? "scrim" : "scrim-transparent");
        fond.setStyle(assombrir ? "" : "-fx-background-color: transparent;");
        fond.setOnMouseClicked(e -> fermer());

        couche.getChildren().setAll(fond, contenu);
        couche.setVisible(true);

        contenu.setOpacity(0);
        FadeTransition fondu = new FadeTransition(Duration.millis(140), contenu);
        fondu.setToValue(1);
        fondu.play();
    }

    public void fermer() {
        if (!couche.isVisible()) return;
        couche.getChildren().clear();
        couche.setVisible(false);
        Runnable rappel = surFermeture;
        surFermeture = null;
        if (rappel != null) rappel.run();
    }
}
