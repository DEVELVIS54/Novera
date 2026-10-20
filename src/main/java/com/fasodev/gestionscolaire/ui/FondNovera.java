package com.fasodev.gestionscolaire.ui;

import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** Halos de couleur très flous (lilas, menthe, blanc) qui donnent de la profondeur au fond. */
public final class FondNovera {

    private FondNovera() {}

    /** Ajoute les halos dans le Pane (placé sous le contenu) et les repositionne selon sa taille. */
    public static void appliquer(Pane fond) {
        Circle lilas = halo(Color.web("#b9b0f0", 0.85), 270);
        Circle menthe = halo(Color.web("#8fe0cf", 0.75), 230);
        Circle blanc = halo(Color.web("#ffffff", 0.55), 210);
        fond.getChildren().addAll(lilas, menthe, blanc);

        Runnable placer = () -> {
            double w = fond.getWidth(), h = fond.getHeight();
            lilas.setCenterX(w * 0.88);  lilas.setCenterY(h * 0.12);
            menthe.setCenterX(w * 0.06); menthe.setCenterY(h * 0.92);
            blanc.setCenterX(w * 0.42);  blanc.setCenterY(h * 0.02);
        };
        fond.widthProperty().addListener((o, a, b) -> placer.run());
        fond.heightProperty().addListener((o, a, b) -> placer.run());

        Rectangle limite = new Rectangle();
        limite.widthProperty().bind(fond.widthProperty());
        limite.heightProperty().bind(fond.heightProperty());
        fond.setClip(limite);
    }

    private static Circle halo(Color couleur, double rayon) {
        Circle c = new Circle(rayon, couleur);
        c.setEffect(new GaussianBlur(110));
        c.setCache(true);
        return c;
    }
}