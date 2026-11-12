package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.services.DashboardService;
import com.fasodev.gestionscolaire.services.SessionManager;
import com.fasodev.gestionscolaire.services.SyncScheduler;
import com.fasodev.gestionscolaire.ui.CommandPalette;
import com.fasodev.gestionscolaire.ui.DashboardView;
import com.fasodev.gestionscolaire.ui.DockMenu;
import com.fasodev.gestionscolaire.ui.DockMenu.Destination;
import com.fasodev.gestionscolaire.ui.Icons;
import com.fasodev.gestionscolaire.ui.AnneeActive;
import com.fasodev.gestionscolaire.ui.Superposition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Fenêtre principale : barre du haut (recherche, synchro, utilisateur), contenu au centre et
 * dock flottant en bas pour naviguer.
 */
public class MainController implements javafx.fxml.Initializable {

    @FXML private StackPane appRoot;
    @FXML private Pane fond;
    @FXML private BorderPane rootPane;
    @FXML private StackPane couche;
    @FXML private VBox dockHolder;
    @FXML private StackPane searchIconHolder;
    @FXML private Label schoolLabel;
    @FXML private Label anneeLabel;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label avatarLabel;
    @FXML private Circle syncStatusCircle;
    @FXML private Label syncStatusLabel;
    @FXML private Label pendingSyncLabel;
    @FXML private HBox userChip;

    private final Map<String, Runnable> pages = new LinkedHashMap<>();
    private Superposition superposition;
    private DockMenu dock;
    private CommandPalette palette;
    private boolean estAdmin;

    /** Quand l'annÃ©e active change : met l'en-tÃªte Ã  jour et revient au tableau de bord. */
    private final ChangeListener<String> surChangementAnnee = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String ancienne, String nouvelle) {
            if (rootPane.getScene() == null || rootPane.getScene().getWindow() == null) {
                AnneeActive.propriete().removeListener(this);   // fenÃªtre fermÃ©e (dÃ©connexion)
                return;
            }
            anneeLabel.setText("AnnÃ©e " + nouvelle);
            aller("accueil");
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        estAdmin = SessionManager.getUtilisateurConnecte() != null
            && SessionManager.getUtilisateurConnecte().isAdmin();

        decorerFond();
        searchIconHolder.getChildren().add(Icons.icone("search", 16));
        afficherUtilisateur();
        AnneeActive.rafraichir();
        anneeLabel.setText("AnnÃ©e " + AnneeActive.lire());
        AnneeActive.propriete().addListener(surChangementAnnee);

        superposition = new Superposition(couche);
        declarerPages();

        dock = new DockMenu(destinations(), estAdmin, superposition);
        dockHolder.getChildren().add(dock.vue());
        palette = new CommandPalette(superposition, dock.destinations(), texte -> {
            RechercheController.preremplir(texte);
            aller("recherche");
        });

        mettreAJourStatutSync(false, 0);
        SyncScheduler.demarrer((online, pending) -> mettreAJourStatutSync(online, pending));

        // Raccourcis : Ctrl+K (palette) et Échap (ferme la fenêtre ouverte)
        rootPane.sceneProperty().addListener((obs, ancienne, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), this::ouvrirPalette);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE && superposition.estOuverte()) {
                    superposition.fermer();
                    e.consume();
                }
            });
        });

        aller("accueil");
    }

    // ------------------------------------------------------------------ navigation

    private void declarerPages() {
        pages.put("accueil", this::afficherTableauDeBord);
        pages.put("etudiants", () -> naviguerVers("/views/etudiants.fxml"));
        pages.put("classes", () -> naviguerVers("/views/classes.fxml"));
        pages.put("matieres", () -> naviguerVers("/views/matieres.fxml"));
        pages.put("reinscriptions", () -> naviguerVers("/views/reinscriptions.fxml"));
        pages.put("recherche", () -> naviguerVers("/views/recherche.fxml"));
        pages.put("notes", () -> naviguerVers("/views/notes.fxml"));
        pages.put("moyennes", () -> naviguerVers("/views/moyennes.fxml"));
        pages.put("passage", () -> naviguerVers("/views/passage_classe.fxml"));
        pages.put("examens", () -> naviguerVers("/views/examen_national.fxml"));
        pages.put("paiements", () -> naviguerVers("/views/paiements.fxml"));
        pages.put("frais", () -> naviguerVers("/views/frais_scolarite.fxml"));
        pages.put("parents", () -> naviguerVers("/views/identifiants_parents.fxml"));
        pages.put("utilisateurs", () -> naviguerVers("/views/utilisateurs.fxml"));
        pages.put("synchronisation", () -> naviguerVers("/views/synchronisation.fxml"));
        pages.put("annee", () -> naviguerVers("/views/annee_scolaire.fxml"));
    }

    private List<Destination> destinations() {
        return List.of(
            dest("accueil", "Tableau de bord", "home", "accueil", false),
            dest("etudiants", "Étudiants", "people", "eleves", false),
            dest("classes", "Classes", "school", "eleves", false),
            dest("matieres", "Matières", "book", "eleves", false),
            dest("reinscriptions", "Réinscriptions", "refresh", "eleves", false),
            dest("recherche", "Recherche", "search", "eleves", false),
            dest("notes", "Notes", "edit", "evaluations", false),
            dest("moyennes", "Calcul des moyennes", "chart", "evaluations", false),
            dest("passage", "Passage en classe", "trend", "evaluations", false),
            dest("examens", "Examens nationaux", "flag", "evaluations", false),
            dest("paiements", "Paiements", "wallet", "finances", false),
            dest("frais", "Frais de scolarité", "receipt", "finances", false),
            dest("parents", "Identifiants parents", "key", "finances", false),
            dest("utilisateurs", "Utilisateurs", "user", "admin", true),
            dest("synchronisation", "Synchronisation", "cloud", "admin", true),
            dest("annee", "AnnÃ©e scolaire", "flag", "admin", true)
        );
    }

    private Destination dest(String id, String titre, String icone, String groupe, boolean reserveAdmin) {
        return new Destination(id, titre, icone, groupe, reserveAdmin, () -> aller(id));
    }

    /** Ouvre une page par son identifiant et met le dock à jour. */
    private void aller(String id) {
        Runnable page = pages.get(id);
        if (page == null) return;
        dock.definirActif(id);
        page.run();
    }

    private void afficherTableauDeBord() {
        try {
            DashboardService.Resume resume = new DashboardService().charger(estAdmin);
            rootPane.setCenter(DashboardView.creer(
                resume, nomUtilisateur(), estAdmin, this::aller, true));
        } catch (RuntimeException e) {
            e.printStackTrace();
            Alert alerte = new Alert(Alert.AlertType.ERROR);
            alerte.setTitle("Tableau de bord");
            alerte.setHeaderText("Impossible de charger les indicateurs");
            alerte.setContentText(e.getMessage());
            alerte.showAndWait();
        }
    }

    private String nomUtilisateur() {
        return SessionManager.getUtilisateurConnecte() != null
            ? SessionManager.getUtilisateurConnecte().getNomUtilisateur() : SessionManager.getNomAffiche();
    }

    private void naviguerVers(String cheminFxml) {
        try {
            URL url = getClass().getResource(cheminFxml);
            if (url == null) {
                url = trouverRessourceSurDisque(cheminFxml);
            }
            if (url == null) {
                throw new IllegalStateException(
                    "Fichier introuvable ni via classpath ni sur disque : " + cheminFxml);
            }

            Node vue = new FXMLLoader(url).load();

            // Les écrans historiques sont présentés dans un panneau en verre aux coins arrondis
            StackPane panneau = new StackPane(vue);
            panneau.getStyleClass().add("glass-panel");
            Rectangle decoupe = new Rectangle();
            decoupe.setArcWidth(48);
            decoupe.setArcHeight(48);
            decoupe.widthProperty().bind(panneau.widthProperty());
            decoupe.heightProperty().bind(panneau.heightProperty());
            panneau.setClip(decoupe);

            StackPane cadre = new StackPane(panneau);
            cadre.setPadding(new Insets(8, 26, 8, 26));
            rootPane.setCenter(cadre);

        } catch (Exception e) {
            e.printStackTrace();
            Alert alerte = new Alert(Alert.AlertType.ERROR);
            alerte.setTitle("Erreur de chargement");
            alerte.setHeaderText("Impossible d'ouvrir cet écran (" + cheminFxml + ")");
            alerte.setContentText(e.getCause() != null ? e.getCause().toString() : e.toString());
            alerte.showAndWait();
        }
    }

    /**
     * Filet de sécurité : reconstruit le chemin disque réel des classes compilées
     * (target/classes/...) et cherche le FXML directement dedans si la résolution classpath échoue.
     */
    private URL trouverRessourceSurDisque(String cheminFxml) {
        try {
            java.io.File dossierClasses = new java.io.File(
                getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            java.io.File fichier = new java.io.File(dossierClasses,
                cheminFxml.replace("/", java.io.File.separator));
            System.out.println("🔍 Recherche fallback sur disque : " + fichier.getAbsolutePath());
            if (fichier.exists()) {
                return fichier.toURI().toURL();
            }
        } catch (Exception ex) {
            System.out.println("Fallback disque échoué : " + ex.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ barre du haut

    @FXML
    public void onPaletteClick(MouseEvent e) {
        ouvrirPalette();
    }

    private void ouvrirPalette() {
        superposition.fermer();
        palette.ouvrir();
    }

    @FXML
    public void onUserClick(MouseEvent e) {
        MenuItem deconnexion = new MenuItem("Se déconnecter");
        deconnexion.setGraphic(Icons.icone("logout", 14));
        deconnexion.setOnAction(ev -> deconnecter());
        ContextMenu menu = new ContextMenu(deconnexion);
        menu.show(userChip, javafx.geometry.Side.BOTTOM, 0, 6);
    }

    private void deconnecter() {
        try {
            SessionManager.deconnecter();
            Parent connexion = new FXMLLoader(getClass().getResource("/views/login.fxml")).load();
            Stage stage = (Stage) rootPane.getScene().getWindow();
            Scene scene = new Scene(connexion, 1000, 650);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void afficherUtilisateur() {
        String nom = SessionManager.getUtilisateurConnecte() != null
            ? SessionManager.getUtilisateurConnecte().getNomUtilisateur() : SessionManager.getNomAffiche();
        userLabel.setText(nom);
        roleLabel.setText(estAdmin ? "Directeur" : "Secrétaire");
        avatarLabel.setText(nom == null || nom.isBlank() ? "?" : nom.substring(0, 1).toUpperCase());
        try {
            String ecole = ConfigurationEcoleRepository.obtenirConfiguration().nom;
            schoolLabel.setText(ecole != null && !ecole.isBlank() ? ecole : "Gestion scolaire");
        } catch (RuntimeException e) {
            schoolLabel.setText("Gestion scolaire");
        }
    }

    public void mettreAJourStatutSync(boolean online, int enAttente) {
        Platform.runLater(() -> {
            syncStatusCircle.setStyle(online ? "-fx-fill: #2fb67c;" : "-fx-fill: #e8923a;");
            syncStatusLabel.setText(online ? "Synchronisé" : "Hors ligne");
            pendingSyncLabel.setText(enAttente > 0 ? "· " + enAttente + " en attente" : "");
            if (estAdmin && dock != null) dock.definirBadge("admin", enAttente);
        });
    }

    // ------------------------------------------------------------------ décor

    /** Halos de couleur très flous (lilas, menthe, blanc) qui donnent de la profondeur au fond. */
    private void decorerFond() {
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

    private Circle halo(Color couleur, double rayon) {
        Circle c = new Circle(rayon, couleur);
        c.setEffect(new GaussianBlur(110));
        c.setCache(true);
        return c;
    }
}
