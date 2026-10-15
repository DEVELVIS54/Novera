package com.fasodev.gestionscolaire.ui;

import com.fasodev.gestionscolaire.services.RechercheService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Palette de commandes (Ctrl+K) : on tape quelques lettres pour aller à une page ou retrouver un
 * élève, sans passer par les menus. Flèches pour choisir, Entrée pour ouvrir, Échap pour fermer.
 */
public final class CommandPalette {

    private record Entree(String icone, String titre, String sousTitre, Runnable action) {}

    private static final int MAX_ENTREES = 8;

    private final Superposition superposition;
    private final List<DockMenu.Destination> destinations;
    private final Consumer<String> rechercher;
    private final RechercheService recherche = new RechercheService();

    private final VBox liste = new VBox(2);
    private final TextField champ = new TextField();
    private List<Entree> entrees = new ArrayList<>();
    private final List<HBox> lignes = new ArrayList<>();
    private int selection = 0;

    public CommandPalette(Superposition superposition, List<DockMenu.Destination> destinations,
                          Consumer<String> rechercher) {
        this.superposition = superposition;
        this.destinations = destinations;
        this.rechercher = rechercher;
    }

    public void ouvrir() {
        champ.clear();
        champ.setPromptText("Aller à une page, ou chercher un élève ou une classe…");
        champ.getStyleClass().setAll("text-field", "palette-field");

        Label aide = new Label("↑ ↓ pour choisir     Entrée pour ouvrir     Échap pour fermer");
        aide.getStyleClass().add("palette-hint");
        aide.setPadding(new Insets(6, 0, 0, 6));

        VBox carte = new VBox(10, champ, liste, aide);
        carte.getStyleClass().add("palette");
        carte.setPrefWidth(600);
        carte.setMaxSize(600, Region.USE_PREF_SIZE);
        StackPane.setAlignment(carte, Pos.TOP_CENTER);
        StackPane.setMargin(carte, new Insets(96, 0, 0, 0));

        champ.textProperty().addListener((obs, ancien, nouveau) -> actualiser(nouveau));
        champ.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                deplacer(1);
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                deplacer(-1);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                activer();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                superposition.fermer();
                e.consume();
            }
        });

        actualiser("");
        superposition.afficher(carte, true, null);
        Platform.runLater(champ::requestFocus);
    }

    // ------------------------------------------------------------------

    private void actualiser(String saisie) {
        String texte = saisie == null ? "" : saisie.trim();
        String q = normaliser(texte);
        List<Entree> resultat = new ArrayList<>();

        for (DockMenu.Destination d : destinations) {
            if (q.isEmpty() || normaliser(d.titre()).contains(q)) {
                resultat.add(new Entree(d.icone(), d.titre(), "Page", () -> {
                    superposition.fermer();
                    d.action().run();
                }));
            }
        }

        if (texte.length() >= 2) {
            try {
                int ajoutes = 0;
                for (RechercheService.EleveTrouve e : recherche.chercherEleves(texte)) {
                    if (ajoutes++ >= 4) break;
                    String detail = e.classe() + (e.matricule() != null ? " · " + e.matricule() : "");
                    resultat.add(new Entree("user", e.nomComplet(), detail, () -> {
                        superposition.fermer();
                        rechercher.accept(e.nomComplet());
                    }));
                }
            } catch (RuntimeException ignore) {
                // la recherche d'élèves est un confort : une erreur ne doit pas bloquer la palette
            }
            resultat.add(new Entree("search", "Rechercher « " + texte + " »", "Élèves et classes", () -> {
                superposition.fermer();
                rechercher.accept(texte);
            }));
        }

        entrees = resultat.size() > MAX_ENTREES ? resultat.subList(0, MAX_ENTREES) : resultat;
        // la ligne « Rechercher » doit rester accessible même si la liste est longue
        if (texte.length() >= 2 && resultat.size() > MAX_ENTREES) {
            entrees = new ArrayList<>(resultat.subList(0, MAX_ENTREES - 1));
            entrees.add(resultat.get(resultat.size() - 1));
        }
        selection = 0;
        rendre();
    }

    private void deplacer(int pas) {
        if (entrees.isEmpty()) return;
        selection = (selection + pas + entrees.size()) % entrees.size();
        marquer();
    }

    /** Met en évidence la ligne choisie sans reconstruire la liste (garde les clics fiables). */
    private void marquer() {
        for (int i = 0; i < lignes.size(); i++) {
            lignes.get(i).getStyleClass().remove("palette-row-sel");
            if (i == selection) lignes.get(i).getStyleClass().add("palette-row-sel");
        }
    }

    private void activer() {
        if (selection >= 0 && selection < entrees.size()) entrees.get(selection).action().run();
    }

    private void rendre() {
        liste.getChildren().clear();
        lignes.clear();
        if (entrees.isEmpty()) {
            Label vide = new Label("Aucun résultat");
            vide.getStyleClass().add("palette-hint");
            vide.setPadding(new Insets(10, 6, 10, 6));
            liste.getChildren().add(vide);
            return;
        }
        for (int i = 0; i < entrees.size(); i++) {
            Entree entree = entrees.get(i);
            final int index = i;

            StackPane boite = new StackPane(Icons.icone(entree.icone(), 16));
            boite.getStyleClass().add("pop-icon-box");

            Label titre = new Label(entree.titre());
            Label detail = new Label(entree.sousTitre());
            detail.getStyleClass().add("muted");
            HBox.setHgrow(titre, Priority.ALWAYS);
            titre.setMaxWidth(Double.MAX_VALUE);

            HBox ligne = new HBox(12, boite, titre, detail);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.getStyleClass().add("palette-row");
            if (i == selection) ligne.getStyleClass().add("palette-row-sel");

            ligne.setOnMouseMoved(e -> {
                if (selection != index) {
                    selection = index;
                    marquer();
                }
            });
            ligne.setOnMouseClicked(e -> entree.action().run());
            lignes.add(ligne);
            liste.getChildren().add(ligne);
        }
    }

    static String normaliser(String texte) {
        String sansAccents = Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase().trim();
    }
}
