package com.fasodev.gestionscolaire.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu « dock » flottant en bas de la fenêtre.
 *
 * Au lieu d'une longue liste de 15 boutons, les pages sont rangées en 5 familles selon le travail
 * de l'école (Élèves, Évaluations, Finances, Réglages). Un clic sur une famille ouvre une petite
 * fenêtre au-dessus d'elle avec ses pages ; la famille de la page ouverte reste mise en évidence.
 * La palette de commandes (Ctrl+K) permet d'aller n'importe où au clavier.
 */
public final class DockMenu {

    public record Destination(String id, String titre, String icone, String groupe,
                              boolean reserveAdmin, Runnable action) {}

    private record Groupe(String cle, String titre, String icone) {}

    private static final List<Groupe> GROUPES = List.of(
        new Groupe("accueil", "Accueil", "home"),
        new Groupe("eleves", "Élèves", "school"),
        new Groupe("evaluations", "Évaluations", "edit"),
        new Groupe("finances", "Finances", "wallet"),
        new Groupe("admin", "Réglages", "gear")
    );

    private final List<Destination> destinations = new ArrayList<>();
    private final Superposition superposition;
    private final HBox dock = new HBox(12);
    private final Map<String, VBox> elements = new LinkedHashMap<>();
    private final Map<String, Label> badges = new LinkedHashMap<>();
    private String destinationActive;
    private String groupeOuvert;

    public DockMenu(List<Destination> toutes, boolean estAdmin, Superposition superposition) {
        this.superposition = superposition;
        for (Destination d : toutes) {
            if (!d.reserveAdmin() || estAdmin) destinations.add(d);
        }

        dock.getStyleClass().add("dock");
        dock.setAlignment(Pos.BOTTOM_CENTER);
        dock.setMaxWidth(Region.USE_PREF_SIZE);

        for (Groupe g : GROUPES) {
            if (destinationsDuGroupe(g.cle()).isEmpty()) continue;   // ex. Réglages pour un Secrétaire
            if (!g.cle().equals("accueil") && elements.size() == 1) {
                Region separateur = new Region();
                separateur.getStyleClass().add("dock-sep");
                separateur.setMinHeight(40);
                separateur.setPrefHeight(40);
                separateur.setMaxHeight(40);
                HBox.setMargin(separateur, new Insets(7, 2, 0, 2));
                dock.getChildren().add(separateur);
            }
            VBox element = creerElement(g);
            elements.put(g.cle(), element);
            dock.getChildren().add(element);
        }
    }

    public Node vue() {
        return dock;
    }

    /** Toutes les pages accessibles à l'utilisateur (pour la palette de commandes). */
    public List<Destination> destinations() {
        return List.copyOf(destinations);
    }

    /** Met en évidence la famille de la page ouverte. */
    public void definirActif(String destinationId) {
        destinationActive = destinationId;
        String groupeActif = null;
        for (Destination d : destinations) {
            if (d.id().equals(destinationId)) groupeActif = d.groupe();
        }
        for (Map.Entry<String, VBox> e : elements.entrySet()) {
            boolean actif = e.getKey().equals(groupeActif);
            e.getValue().getStyleClass().remove("dock-item-actif");
            if (actif) e.getValue().getStyleClass().add("dock-item-actif");
            // le point sous l'icône
            Node point = e.getValue().getChildren().get(2);
            point.setVisible(actif);
        }
    }

    /** Pastille de compteur sur une famille (0 = masquée). */
    public void definirBadge(String groupe, int nombre) {
        Label badge = badges.get(groupe);
        if (badge == null) return;
        badge.setText(nombre > 99 ? "99+" : String.valueOf(nombre));
        badge.getParent().setVisible(nombre > 0);
    }

    // ------------------------------------------------------------------

    private VBox creerElement(Groupe groupe) {
        Region icone = Icons.icone(groupe.icone(), 22);

        // pastille de compteur (en haut à droite de la tuile)
        Label nombre = new Label("0");
        StackPane pastille = new StackPane(nombre);
        pastille.getStyleClass().add("dock-badge");
        pastille.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pastille.setPadding(new Insets(0, 5, 0, 5));
        pastille.setVisible(false);
        StackPane.setAlignment(pastille, Pos.TOP_RIGHT);
        StackPane.setMargin(pastille, new Insets(-5, -5, 0, 0));
        badges.put(groupe.cle(), nombre);

        StackPane tuile = new StackPane(icone, pastille);
        tuile.getStyleClass().add("dock-tile");

        Label legende = new Label(groupe.titre());
        legende.getStyleClass().add("dock-caption");

        Region point = new Region();
        point.getStyleClass().add("dock-dot");
        point.setMinSize(6, 6);
        point.setPrefSize(6, 6);
        point.setMaxSize(6, 6);
        point.setVisible(false);

        VBox element = new VBox(3, tuile, legende, point);
        element.setAlignment(Pos.CENTER);
        element.setMinWidth(66);
        element.getStyleClass().add("dock-item");

        element.setOnMouseEntered(e -> animer(tuile, -7, 1.08));
        element.setOnMouseExited(e -> animer(tuile, 0, 1.0));
        element.setOnMouseClicked(e -> surClic(groupe, tuile));
        return element;
    }

    private void surClic(Groupe groupe, Node tuile) {
        List<Destination> pages = destinationsDuGroupe(groupe.cle());
        if (pages.size() == 1) {                 // Accueil : pas de fenêtre, on y va directement
            superposition.fermer();
            pages.get(0).action().run();
            return;
        }
        if (groupe.cle().equals(groupeOuvert)) {  // 2e clic : on referme
            superposition.fermer();
            return;
        }
        ouvrirFenetre(groupe, pages, tuile);
    }

    private void ouvrirFenetre(Groupe groupe, List<Destination> pages, Node tuile) {
        final double largeur = 250;

        VBox fenetre = new VBox(2);
        fenetre.getStyleClass().add("popover");
        fenetre.setPrefWidth(largeur);
        fenetre.setMaxSize(largeur, Region.USE_PREF_SIZE);

        Label titre = new Label(groupe.titre().toUpperCase());
        titre.getStyleClass().add("popover-title");
        titre.setPadding(new Insets(0, 0, 6, 6));
        fenetre.getChildren().add(titre);

        for (Destination page : pages) {
            StackPane boite = new StackPane(Icons.icone(page.icone(), 16));
            boite.getStyleClass().add("pop-icon-box");

            Label nom = new Label(page.titre());
            HBox ligne = new HBox(12, boite, nom);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.getStyleClass().add("pop-item");
            if (page.id().equals(destinationActive)) ligne.getStyleClass().add("pop-item-actif");
            HBox.setHgrow(nom, Priority.ALWAYS);

            ligne.setOnMouseClicked(e -> {
                superposition.fermer();
                page.action().run();
            });
            fenetre.getChildren().add(ligne);
        }

        // Position : centrée sur la tuile, juste au-dessus du dock
        StackPane couche = superposition.couche();
        Bounds b = tuile.localToScene(tuile.getBoundsInLocal());
        Point2D haut = couche.sceneToLocal(b.getCenterX(), b.getMinY());
        double x = Math.max(14, Math.min(haut.getX() - largeur / 2, couche.getWidth() - largeur - 14));
        double bas = Math.max(0, couche.getHeight() - haut.getY() + 14);

        StackPane.setAlignment(fenetre, Pos.BOTTOM_LEFT);
        StackPane.setMargin(fenetre, new Insets(0, 0, bas, x));

        groupeOuvert = groupe.cle();
        superposition.afficher(fenetre, false, () -> groupeOuvert = null);
    }

    private List<Destination> destinationsDuGroupe(String cle) {
        List<Destination> liste = new ArrayList<>();
        for (Destination d : destinations) {
            if (d.groupe().equals(cle)) liste.add(d);
        }
        return liste;
    }

    private static void animer(Node noeud, double decalageY, double echelle) {
        new Timeline(new KeyFrame(Duration.millis(140),
            new KeyValue(noeud.translateYProperty(), decalageY, Interpolator.EASE_OUT),
            new KeyValue(noeud.scaleXProperty(), echelle, Interpolator.EASE_OUT),
            new KeyValue(noeud.scaleYProperty(), echelle, Interpolator.EASE_OUT)
        )).play();
    }
}
