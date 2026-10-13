package com.fasodev.gestionscolaire.ui;

import com.fasodev.gestionscolaire.services.DashboardService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Construit l'écran « Tableau de bord » à partir des indicateurs calculés. */
public final class DashboardView {

    private DashboardView() {}

    public static Node creer(DashboardService.Resume r, String nomUtilisateur) {

        Label titre = new Label("Tableau de bord");
        titre.getStyleClass().add("section-title");

        Label sousTitre = new Label("Bienvenue, " + nomUtilisateur);
        sousTitre.getStyleClass().add("info-label");

        FlowPane cartes = new FlowPane(15, 15);
        cartes.getChildren().addAll(
            carte("👨‍🎓", String.valueOf(r.elevesActifs()), "Élèves actifs"),
            carte("🏫", String.valueOf(r.classes()), "Classes"),
            carte("📚", String.valueOf(r.matieres()), "Matières"),
            carte("🔑", String.valueOf(r.parentsActifs()), "Accès parents actifs"),
            carte("🎓", String.valueOf(r.elevesSubventionnes()), "Élèves subventionnés"),
            carte("💰", String.format("%,.0f FCFA", r.totalEncaisse()), "Total encaissé"),
            carte("☁️", String.valueOf(r.operationsEnAttente()), "Opérations à synchroniser")
        );

        Label titreEffectifs = new Label("Effectif par classe");
        titreEffectifs.getStyleClass().add("section-title");

        GridPane effectifs = new GridPane();
        effectifs.setHgap(12);
        effectifs.setVgap(8);
        ColumnConstraints cNom = new ColumnConstraints(160);
        ColumnConstraints cBarre = new ColumnConstraints();
        cBarre.setHgrow(Priority.ALWAYS);
        ColumnConstraints cNombre = new ColumnConstraints(50);
        effectifs.getColumnConstraints().addAll(cNom, cBarre, cNombre);

        int max = r.effectifs().stream().mapToInt(DashboardService.EffectifClasse::effectif).max().orElse(1);
        int ligne = 0;
        for (DashboardService.EffectifClasse e : r.effectifs()) {
            ProgressBar barre = new ProgressBar(max > 0 ? (double) e.effectif() / max : 0);
            barre.setMaxWidth(Double.MAX_VALUE);
            effectifs.add(new Label(e.classe()), 0, ligne);
            effectifs.add(barre, 1, ligne);
            effectifs.add(new Label(String.valueOf(e.effectif())), 2, ligne);
            ligne++;
        }
        if (r.effectifs().isEmpty()) {
            effectifs.add(new Label("Aucune classe pour le moment."), 0, 0);
        }

        VBox contenu = new VBox(18, titre, sousTitre, cartes, titreEffectifs, effectifs);
        contenu.setPadding(new Insets(20));
        contenu.getStyleClass().add("content-area");

        ScrollPane scroll = new ScrollPane(contenu);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        return scroll;
    }

    private static Node carte(String icone, String valeur, String legende) {
        Label lblIcone = new Label(icone);
        lblIcone.setStyle("-fx-font-size: 22px;");
        Label lblValeur = new Label(valeur);
        lblValeur.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label lblLegende = new Label(legende);
        lblLegende.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        VBox carte = new VBox(4, lblIcone, lblValeur, lblLegende);
        carte.setAlignment(Pos.CENTER_LEFT);
        carte.setPadding(new Insets(14, 18, 14, 18));
        carte.setPrefWidth(190);
        carte.setStyle("-fx-background-color: white; -fx-background-radius: 10; "
            + "-fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        return carte;
    }
}
