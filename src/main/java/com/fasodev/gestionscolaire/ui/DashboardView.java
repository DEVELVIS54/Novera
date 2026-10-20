package com.fasodev.gestionscolaire.ui;

import com.fasodev.gestionscolaire.services.DashboardService;
import com.fasodev.gestionscolaire.services.DashboardService.Alerte;
import com.fasodev.gestionscolaire.services.DashboardService.ClasseClassee;
import com.fasodev.gestionscolaire.services.DashboardService.Resume;
import com.fasodev.gestionscolaire.services.DashboardService.Statuts;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Écran « Tableau de bord » : cartes en verre sur fond teal, avec accès direct aux actions utiles. */
public final class DashboardView {

    private DashboardView() {}

    /**
     * @param aller appelé avec l'identifiant d'une page (« moyennes », « reinscriptions »…) quand
     *              l'utilisateur clique sur une action du tableau de bord
     */
    public static Node creer(Resume r, String nom, boolean estAdmin, Consumer<String> aller, boolean animer) {

        Label bienvenue = new Label(salutation() + ", " + nom);
        bienvenue.getStyleClass().add("dash-welcome");

        Label titre = new Label("Tableau de bord");
        titre.getStyleClass().add("dash-title");

        Label date = new Label(dateDuJour());
        date.getStyleClass().add("dash-welcome-soft");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox ligneTitre = new HBox(titre, espace, date);
        ligneTitre.setAlignment(Pos.BOTTOM_LEFT);

        VBox entete = new VBox(0, bienvenue, ligneTitre);

        Node aFaire = carteAFaire(r.alertes(), aller);
        Node classes = carteClasses(r.classement(), aller);
        Node situation = carteSituation(r.statuts(), r.elevesSubventionnes(), aller);
        Node tuiles = tuiles(r);
        Node banniere = banniere(r, estAdmin, aller);

        GridPane grille = new GridPane();
        grille.setHgap(18);
        grille.setVgap(18);
        ColumnConstraints gauche = new ColumnConstraints();
        gauche.setPercentWidth(55);
        ColumnConstraints droite = new ColumnConstraints();
        droite.setPercentWidth(45);
        grille.getColumnConstraints().addAll(gauche, droite);

        grille.add(aFaire, 0, 0);
        grille.add(classes, 0, 1, 1, 2);
        grille.add(situation, 1, 0);
        grille.add(tuiles, 1, 1);
        grille.add(banniere, 1, 2);
        for (Node n : List.of(aFaire, classes, situation, tuiles, banniere)) {
            GridPane.setFillWidth(n, true);
            GridPane.setFillHeight(n, true);
            GridPane.setVgrow(n, Priority.SOMETIMES);
        }

        VBox contenu = new VBox(12, entete, grille);
        contenu.setPadding(new Insets(4, 26, 14, 26));

        ScrollPane defilement = new ScrollPane(contenu);
        defilement.setFitToWidth(true);
        defilement.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        defilement.getStyleClass().add("transparent-scroll");
        defilement.setFocusTraversable(false);
        defilement.setVvalue(0);

        if (animer) {
            int i = 0;
            for (Node n : List.of(entete, aFaire, situation, classes, tuiles, banniere)) {
                apparaitre(n, 60 * i++);
            }
        }
        return defilement;
    }

    // ------------------------------------------------------------------ cartes

    private static Node carteAFaire(List<Alerte> alertes, Consumer<String> aller) {
        VBox corps = new VBox(6);
        if (alertes.isEmpty()) {
            corps.getChildren().add(ligneAFaire("check", "chip-green", "Tout est à jour. Beau travail !", null, aller));
        } else {
            int n = 0;
            for (Alerte a : alertes) {
                if (n++ >= 3) break;
                corps.getChildren().add(ligneAFaire(a.icone(), couleurPourIcone(a.icone()), a.texte(), a.cible(), aller));
            }
        }
        String sousTitre = alertes.isEmpty() ? "Rien d'urgent" : alertes.size() + " point(s) demandent votre attention";
        return carte(enTete("À faire aujourd'hui", null, null, aller), muted(sousTitre), corps);
    }

    private static Node ligneAFaire(String icone, String couleur, String texte, String cible, Consumer<String> aller) {
        StackPane puce = puce(icone, couleur, 16);
        Label libelle = new Label(texte);
        libelle.getStyleClass().add("row-text");
        libelle.setWrapText(true);
        HBox.setHgrow(libelle, Priority.ALWAYS);
        libelle.setMaxWidth(Double.MAX_VALUE);

        HBox ligne = new HBox(12, puce, libelle);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.getStyleClass().add("todo-row");
        if (cible != null) {
            Label fleche = new Label("›");
            fleche.getStyleClass().add("todo-go");
            ligne.getChildren().add(fleche);
            ligne.setOnMouseClicked(e -> aller.accept(cible));
        }
        return ligne;
    }

    private static Node carteSituation(Statuts s, int subventionnes, Consumer<String> aller) {
        HBox piste = new HBox();
        piste.getStyleClass().add("bar-track");
        piste.setMinHeight(10);
        piste.setPrefHeight(10);
        piste.setMaxHeight(10);
        Rectangle decoupe = new Rectangle();
        decoupe.setArcWidth(12);
        decoupe.setArcHeight(12);
        decoupe.widthProperty().bind(piste.widthProperty());
        decoupe.heightProperty().bind(piste.heightProperty());
        piste.setClip(decoupe);

        int total = Math.max(1, s.total());
        ajouterSegment(piste, "seg-actifs", s.actifs(), total);
        ajouterSegment(piste, "seg-attente", s.enAttenteReinscription(), total);
        ajouterSegment(piste, "seg-sortis", s.sortis(), total);
        ajouterSegment(piste, "seg-partis", s.partis(), total);

        HBox colonnes = new HBox(0,
            statutColonne("ACTIFS", s.actifs(), "seg-actifs"),
            statutColonne("EN ATTENTE", s.enAttenteReinscription(), "seg-attente"),
            statutColonne("SORTIS", s.sortis(), "seg-sortis"),
            statutColonne("PARTIS", s.partis(), "seg-partis"));
        colonnes.getStyleClass().add("stat-box");
        colonnes.setPadding(new Insets(10, 12, 10, 12));

        StackPane puceEtat = puce("flag", "chip-green", 14);
        puceEtat.setMaxSize(30, 30);
        puceEtat.setMinSize(30, 30);
        puceEtat.setPrefSize(30, 30);
        Label etat = new Label(subventionnes == 0
            ? "Aucun élève subventionné par l'État"
            : subventionnes + (subventionnes == 1 ? " élève subventionné" : " élèves subventionnés") + " par l'État");
        etat.getStyleClass().add("row-text");
        HBox ligneEtat = new HBox(10, puceEtat, etat);
        ligneEtat.setAlignment(Pos.CENTER_LEFT);

        VBox carte = carte(enTete("Situation des élèves", "Voir les élèves", "etudiants", aller),
            piste, colonnes, ligneEtat);
        carte.setAlignment(Pos.CENTER_LEFT);
        return carte;
    }

    private static void ajouterSegment(HBox piste, String classe, int valeur, int total) {
        if (valeur <= 0) return;
        Region segment = new Region();
        segment.getStyleClass().add(classe);
        segment.prefWidthProperty().bind(piste.widthProperty().multiply((double) valeur / total));
        segment.setMinWidth(0);
        piste.getChildren().add(segment);
    }

    private static Node statutColonne(String legende, int valeur, String classeCouleur) {
        Region pastille = new Region();
        pastille.getStyleClass().add(classeCouleur);
        pastille.setMinSize(8, 8);
        pastille.setPrefSize(8, 8);
        pastille.setMaxSize(8, 8);
        pastille.setStyle("-fx-background-radius: 4;");

        Label l = new Label(legende);
        l.getStyleClass().add("muted-cap");
        HBox haut = new HBox(5, pastille, l);
        haut.setAlignment(Pos.CENTER_LEFT);

        Label v = new Label(String.valueOf(valeur));
        v.getStyleClass().add("stat-value");

        VBox colonne = new VBox(2, haut, v);
        HBox.setHgrow(colonne, Priority.ALWAYS);
        colonne.setMaxWidth(Double.MAX_VALUE);
        return colonne;
    }

    private static Node carteClasses(List<ClasseClassee> classement, Consumer<String> aller) {
        GridPane table = new GridPane();
        table.setVgap(0);
        table.setHgap(8);
        ColumnConstraints cRang = new ColumnConstraints(30);
        ColumnConstraints cNom = new ColumnConstraints();
        cNom.setHgrow(Priority.ALWAYS);
        ColumnConstraints cEff = new ColumnConstraints(70);
        ColumnConstraints cMoy = new ColumnConstraints(80);
        table.getColumnConstraints().addAll(cRang, cNom, cEff, cMoy);

        table.add(cap("#"), 0, 0);
        table.add(cap("CLASSE"), 1, 0);
        table.add(cap("ÉLÈVES"), 2, 0);
        table.add(cap("MOYENNE"), 3, 0);

        if (classement.isEmpty()) {
            Label vide = new Label("Aucune classe pour le moment. Commencez par en créer une.");
            vide.getStyleClass().add("row-text");
            vide.setWrapText(true);
            table.add(vide, 0, 1, 4, 1);
        }

        int ligne = 1;
        for (ClasseClassee c : classement) {
            boolean premiere = ligne == 1 && c.moyenne() != null;
            String style = premiere ? "row-strong" : "row-text";

            Label rang = new Label(String.valueOf(ligne));
            rang.getStyleClass().add("muted");

            Label initiales = new Label(initiales(c.nom()));
            StackPane badge = new StackPane(initiales);
            badge.getStyleClass().add("rank-badge");
            Label nom = new Label(c.nom());
            nom.getStyleClass().add(style);
            HBox cellNom = new HBox(10, badge, nom);
            cellNom.setAlignment(Pos.CENTER_LEFT);

            Label eff = new Label(String.valueOf(c.effectif()));
            eff.getStyleClass().add(style);
            Label moy = new Label(c.moyenne() == null ? "—" : String.format(Locale.FRENCH, "%.2f", c.moyenne()));
            moy.getStyleClass().add(style);

            List<Node> cellules = List.of(rang, cellNom, eff, moy);
            for (int col = 0; col < cellules.size(); col++) {
                HBox cellule = new HBox(cellules.get(col));
                cellule.setAlignment(Pos.CENTER_LEFT);
                cellule.setPadding(new Insets(6, 0, 6, 0));
                cellule.getStyleClass().add("row-line");
                table.add(cellule, col, ligne);
            }
            ligne++;
        }

        return carte(enTete("Classement des classes", "Voir tout", "classes", aller),
            muted("Moyenne du dernier trimestre calculé"), table);
    }

    private static Node tuiles(Resume r) {
        GridPane grille = new GridPane();
        grille.setHgap(18);
        grille.setVgap(18);
        ColumnConstraints a = new ColumnConstraints();
        a.setPercentWidth(50);
        ColumnConstraints b = new ColumnConstraints();
        b.setPercentWidth(50);
        grille.getColumnConstraints().addAll(a, b);

        grille.add(tuile("school", "chip-violet", "ÉLÈVES ACTIFS", String.valueOf(r.elevesActifs()), ""), 0, 0);
        grille.add(tuile("wallet", "chip-pink", "TOTAL ENCAISSÉ", compact(r.totalEncaisse()), "FCFA"), 1, 0);
        grille.add(tuile("key", "chip-orange", "ACCÈS PARENTS", String.valueOf(r.parentsActifs()), ""), 0, 1);
        grille.add(tuile("star", "chip-teal", "MOYENNE GÉNÉRALE",
            r.moyenneGenerale() == null ? "—" : String.format(Locale.FRENCH, "%.1f", r.moyenneGenerale()),
            r.moyenneGenerale() == null ? "" : "/20"), 1, 1);
        return grille;
    }

    private static Node tuile(String icone, String couleur, String legende, String valeur, String unite) {
        Label l = new Label(legende);
        l.getStyleClass().add("muted-cap");
        Label v = new Label(valeur);
        v.getStyleClass().add("stat-value");
        HBox ligneValeur = new HBox(4, v);
        ligneValeur.setAlignment(Pos.BASELINE_LEFT);
        if (!unite.isEmpty()) {
            Label u = new Label(unite);
            u.getStyleClass().add("stat-unit");
            ligneValeur.getChildren().add(u);
        }
        VBox texte = new VBox(2, l, ligneValeur);
        texte.setAlignment(Pos.CENTER_LEFT);
        HBox tuile = new HBox(12, puce(icone, couleur, 16), texte);
        tuile.setAlignment(Pos.CENTER_LEFT);
        tuile.getStyleClass().add("glass-card");
        tuile.setPadding(new Insets(14, 16, 14, 16));
        return tuile;
    }

    private static Node banniere(Resume r, boolean estAdmin, Consumer<String> aller) {
        boolean envoyer = estAdmin && r.operationsEnAttente() > 0;
        String texte = envoyer ? "Envoyez vos modifications au serveur" : "Calculez les moyennes du trimestre";
        String bouton = envoyer ? "Synchroniser maintenant" : "Aller aux moyennes";
        String cible = envoyer ? "synchronisation" : "moyennes";

        Label kicker = new Label("PENSEZ-Y");
        kicker.getStyleClass().add("banner-kicker");
        Label titre = new Label(texte);
        titre.getStyleClass().add("banner-title");
        titre.setWrapText(true);
        titre.setMaxWidth(250);
        Button action = new Button(bouton);
        action.getStyleClass().add("btn-white");
        action.setOnAction(e -> aller.accept(cible));
        action.setFocusTraversable(false);

        VBox gauche = new VBox(8, kicker, titre, action);
        gauche.setAlignment(Pos.CENTER_LEFT);
        gauche.setPadding(new Insets(20, 0, 20, 24));

        StackPane banniere = new StackPane(decor(), gauche);
        StackPane.setAlignment(gauche, Pos.CENTER_LEFT);
        banniere.getStyleClass().add("banner-card");
        banniere.setMinHeight(150);

        Rectangle decoupe = new Rectangle();
        decoupe.setArcWidth(44);
        decoupe.setArcHeight(44);
        decoupe.widthProperty().bind(banniere.widthProperty());
        decoupe.heightProperty().bind(banniere.heightProperty());
        banniere.setClip(decoupe);
        return banniere;
    }

    /** Petites formes 3D façon illustration, à droite de la bannière. */
    public static Node decor() {
        Pane formes = new Pane();
        formes.setMinSize(230, 165);
        formes.setPrefSize(230, 165);
        formes.setMaxSize(230, 165);
        formes.setMouseTransparent(true);

        Ellipse socle = new Ellipse(120, 140, 88, 24);
        socle.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#58b4f5")), new Stop(1, Color.web("#1e6fd0"))));

        Rectangle cube = new Rectangle(58, 70, 46, 46);
        cube.setArcWidth(14);
        cube.setArcHeight(14);
        cube.setFill(Color.web("#ffffff", 0.92));
        cube.setRotate(-14);

        Rectangle petit = new Rectangle(112, 96, 30, 30);
        petit.setArcWidth(10);
        petit.setArcHeight(10);
        petit.setFill(Color.web("#d6f4f4", 0.95));
        petit.setRotate(22);

        Circle sphere = new Circle(158, 52, 22);
        sphere.setFill(new RadialGradient(0, 0, 150, 44, 26, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#e4fb8a")), new Stop(1, Color.web("#6fb021"))));

        Circle anneau = new Circle(186, 104, 16);
        anneau.setFill(Color.TRANSPARENT);
        anneau.setStroke(Color.web("#e9a6ff"));
        anneau.setStrokeWidth(5);

        Circle bille = new Circle(86, 40, 8);
        bille.setFill(Color.web("#7ee0e6"));

        DropShadow ombre = new DropShadow(10, 0, 5, Color.web("#04353b", 0.35));
        for (Node n : List.of(cube, petit, sphere, anneau, bille)) n.setEffect(ombre);

        formes.getChildren().addAll(socle, cube, petit, sphere, anneau, bille);
        StackPane.setAlignment(formes, Pos.CENTER_RIGHT);
        return formes;
    }

    // ------------------------------------------------------------------ briques

    private static VBox carte(Node... enfants) {
        VBox carte = new VBox(12, enfants);
        carte.getStyleClass().add("glass-card");
        carte.setPadding(new Insets(16, 20, 16, 20));
        carte.setMaxWidth(Double.MAX_VALUE);
        carte.setMaxHeight(Double.MAX_VALUE);
        return carte;
    }

    private static Node enTete(String titre, String lien, String cible, Consumer<String> aller) {
        Label t = new Label(titre);
        t.getStyleClass().add("card-title");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox ligne = new HBox(t, espace);
        ligne.setAlignment(Pos.CENTER_LEFT);
        if (lien != null) {
            Label l = new Label(lien);
            l.getStyleClass().add("card-link");
            l.setOnMouseClicked(e -> aller.accept(cible));
            ligne.getChildren().add(l);
        }
        return ligne;
    }

    private static StackPane puce(String icone, String couleur, double tailleIcone) {
        StackPane puce = new StackPane(Icons.icone(icone, tailleIcone));
        puce.getStyleClass().addAll("chip", couleur);
        puce.setMaxSize(36, 36);
        return puce;
    }

    private static Label muted(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("muted");
        return l;
    }

    private static Label cap(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("muted-cap");
        l.setPadding(new Insets(0, 0, 6, 0));
        return l;
    }

    private static String couleurPourIcone(String icone) {
        return switch (icone) {
            case "refresh" -> "chip-orange";
            case "chart" -> "chip-violet";
            case "key" -> "chip-pink";
            case "cloud" -> "chip-teal";
            default -> "chip-blue";
        };
    }

    private static void apparaitre(Node noeud, int delaiMs) {
        noeud.setOpacity(0);
        noeud.setTranslateY(14);
        FadeTransition fondu = new FadeTransition(Duration.millis(320), noeud);
        fondu.setToValue(1);
        TranslateTransition glisse = new TranslateTransition(Duration.millis(320), noeud);
        glisse.setToY(0);
        PauseTransition attente = new PauseTransition(Duration.millis(delaiMs));
        attente.setOnFinished(e -> {
            fondu.play();
            glisse.play();
        });
        attente.play();
    }

    // ------------------------------------------------------------------ textes

    static String initiales(String nom) {
        if (nom == null || nom.isBlank()) return "?";
        String propre = nom.trim();
        String[] mots = propre.split("\\s+");
        if (mots.length >= 2) {   // « 6ème B » -> « 6B »
            return ("" + mots[0].charAt(0) + mots[mots.length - 1].charAt(0)).toUpperCase();
        }
        return propre.length() <= 2 ? propre.toUpperCase() : propre.substring(0, 2).toUpperCase();
    }

    static String compact(double montant) {
        if (montant >= 1_000_000) return String.format(Locale.FRENCH, "%.2f M", montant / 1_000_000);
        if (montant >= 10_000) return String.format(Locale.FRENCH, "%.0f k", montant / 1_000);
        return String.format(Locale.FRENCH, "%,.0f", montant);
    }

    private static String salutation() {
        return LocalTime.now().getHour() < 18 ? "Bonjour" : "Bonsoir";
    }

    private static String dateDuJour() {
        String texte = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH));
        return texte.substring(0, 1).toUpperCase() + texte.substring(1);
    }
}
