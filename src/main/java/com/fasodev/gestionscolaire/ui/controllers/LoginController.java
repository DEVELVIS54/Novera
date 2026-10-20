package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Utilisateur;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.services.AuthService;
import com.fasodev.gestionscolaire.ui.DashboardView;
import com.fasodev.gestionscolaire.ui.FondNovera;
import com.fasodev.gestionscolaire.ui.Icons;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/** Écran de connexion : mêmes couleurs que le reste de l'application, avec quelques conforts d'usage. */
public class LoginController implements Initializable {

    private static final int ESSAIS_MAX = 5;
    private static final int BLOCAGE_SECONDES = 30;
    private static final String CLE_DERNIER_UTILISATEUR = "dernierUtilisateur";

    @FXML private Pane fond;
    @FXML private HBox carte;
    @FXML private StackPane illustrationHolder;
    @FXML private VBox fonctionnalites;
    @FXML private Label ecoleLabel;

    @FXML private HBox coqueUtilisateur;
    @FXML private HBox coqueMotDePasse;
    @FXML private StackPane iconeUtilisateur;
    @FXML private StackPane iconeCadenas;
    @FXML private TextField nomUtilisateurField;
    @FXML private PasswordField motDePasseField;
    @FXML private TextField motDePasseVisibleField;
    @FXML private Button oeilButton;
    @FXML private Label erreurLabel;
    @FXML private Button connexionButton;

    private final AuthService authService = new AuthService();
    private final Preferences preferences = Preferences.userNodeForPackage(LoginController.class);

    private int echecs = 0;
    private Timeline blocage;
    private boolean motDePasseAffiche = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        FondNovera.appliquer(fond);

        iconeUtilisateur.getChildren().add(Icons.icone("user", 18));
        iconeCadenas.getChildren().add(Icons.icone("lock", 18));
        oeilButton.setGraphic(Icons.icone("eye", 20, 14));
        oeilButton.setFocusTraversable(false);

        illustrationHolder.getChildren().add(DashboardView.decor());
        for (String texte : new String[] {
                "Élèves, notes et bulletins",
                "Paiements et reçus PDF",
                "Fonctionne même sans internet"}) {
            fonctionnalites.getChildren().add(ligneFonctionnalite(texte));
        }

        try {
            String ecole = ConfigurationEcoleRepository.obtenirConfiguration().nom;
            ecoleLabel.setText(ecole != null && !ecole.isBlank() ? ecole : "Gestion scolaire");
        } catch (RuntimeException e) {
            ecoleLabel.setText("Gestion scolaire");
        }

        // Le champ « visible » reprend le texte du champ masqué (œil)
        motDePasseVisibleField.textProperty().bindBidirectional(motDePasseField.textProperty());

        suivreFocus(nomUtilisateurField, coqueUtilisateur);
        suivreFocus(motDePasseField, coqueMotDePasse);
        suivreFocus(motDePasseVisibleField, coqueMotDePasse);

        nomUtilisateurField.textProperty().addListener((o, a, b) -> masquerErreur());
        motDePasseField.textProperty().addListener((o, a, b) -> masquerErreur());

        nomUtilisateurField.setOnAction(e -> champMotDePasseActif().requestFocus());
        motDePasseField.setOnAction(e -> onConnexionClick());
        motDePasseVisibleField.setOnAction(e -> onConnexionClick());

        // On se souvient du dernier compte : il ne reste que le mot de passe à saisir
        String dernier = preferences.get(CLE_DERNIER_UTILISATEUR, "");
        if (!dernier.isBlank()) {
            nomUtilisateurField.setText(dernier);
            Platform.runLater(motDePasseField::requestFocus);
        } else {
            Platform.runLater(nomUtilisateurField::requestFocus);
        }
    }

    @FXML
    public void onConnexionClick() {
        if (blocage != null) return;   // période d'attente après trop d'échecs

        String nomUtilisateur = nomUtilisateurField.getText() == null ? "" : nomUtilisateurField.getText().trim();
        String motDePasse = motDePasseField.getText() == null ? "" : motDePasseField.getText();

        if (nomUtilisateur.isEmpty() || motDePasse.isEmpty()) {
            afficherErreur("Saisissez votre nom d'utilisateur et votre mot de passe.");
            return;
        }

        connexionButton.setDisable(true);
        connexionButton.setText("Connexion…");

        Utilisateur utilisateur;
        try {
            utilisateur = authService.connecter(nomUtilisateur, motDePasse);
        } catch (RuntimeException e) {
            connexionButton.setDisable(false);
            connexionButton.setText("Se connecter");
            afficherErreur("Erreur technique : " + e.getMessage());
            return;
        }

        if (utilisateur == null) {
            connexionButton.setDisable(false);
            connexionButton.setText("Se connecter");
            refuser();
            return;
        }

        preferences.put(CLE_DERNIER_UTILISATEUR, utilisateur.getNomUtilisateur());
        ouvrirFenetrePrincipale();
    }

    @FXML
    public void onOeilClick() {
        motDePasseAffiche = !motDePasseAffiche;

        motDePasseField.setVisible(!motDePasseAffiche);
        motDePasseField.setManaged(!motDePasseAffiche);
        motDePasseVisibleField.setVisible(motDePasseAffiche);
        motDePasseVisibleField.setManaged(motDePasseAffiche);
        oeilButton.setGraphic(motDePasseAffiche ? Icons.icone("eye_off", 20, 17) : Icons.icone("eye", 20, 14));

        TextField actif = champMotDePasseActif();
        actif.requestFocus();
        actif.positionCaret(actif.getText().length());
    }

    // ------------------------------------------------------------------ erreurs et essais

    /** Mauvais identifiants : message, secousse de la carte, et blocage temporaire après 5 échecs. */
    private void refuser() {
        echecs++;
        coqueMotDePasse.getStyleClass().add("lg-shell-erreur");
        secouerCarte();

        if (echecs >= ESSAIS_MAX) {
            bloquer();
            return;
        }
        int restants = ESSAIS_MAX - echecs;
        String suite = restants <= 2 ? " (" + restants + " essai" + (restants > 1 ? "s" : "") + " restant"
            + (restants > 1 ? "s" : "") + ")" : "";
        afficherErreur("Nom d'utilisateur ou mot de passe incorrect." + suite);
    }

    private void bloquer() {
        connexionButton.setDisable(true);
        final int[] restant = {BLOCAGE_SECONDES};
        afficherErreur("Trop d'essais. Réessayez dans " + restant[0] + " s.");

        blocage = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            restant[0]--;
            if (restant[0] > 0) {
                erreurLabel.setText("Trop d'essais. Réessayez dans " + restant[0] + " s.");
            } else {
                blocage.stop();
                blocage = null;
                echecs = 0;
                connexionButton.setDisable(false);
                masquerErreur();
            }
        }));
        blocage.setCycleCount(BLOCAGE_SECONDES);
        blocage.play();
    }

    private void afficherErreur(String message) {
        erreurLabel.setText(message);
        erreurLabel.setVisible(true);
    }

    private void masquerErreur() {
        if (blocage != null) return;   // le décompte reste affiché
        erreurLabel.setVisible(false);
        coqueMotDePasse.getStyleClass().remove("lg-shell-erreur");
    }

    private void secouerCarte() {
        new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(carte.translateXProperty(), 0)),
            new KeyFrame(Duration.millis(60), new KeyValue(carte.translateXProperty(), -10)),
            new KeyFrame(Duration.millis(120), new KeyValue(carte.translateXProperty(), 10)),
            new KeyFrame(Duration.millis(180), new KeyValue(carte.translateXProperty(), -7)),
            new KeyFrame(Duration.millis(240), new KeyValue(carte.translateXProperty(), 7)),
            new KeyFrame(Duration.millis(300), new KeyValue(carte.translateXProperty(), 0))
        ).play();
    }

    // ------------------------------------------------------------------ détails d'interface

    private HBox ligneFonctionnalite(String texte) {
        StackPane coche = new StackPane(Icons.icone("check", 11));
        coche.getStyleClass().add("lg-check");
        Label label = new Label(texte);
        label.getStyleClass().add("lg-feature");
        HBox ligne = new HBox(10, coche, label);
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    private void suivreFocus(TextField champ, HBox coque) {
        champ.focusedProperty().addListener((obs, avant, maintenant) -> {
            coque.getStyleClass().remove("lg-shell-focus");
            if (maintenant) coque.getStyleClass().add("lg-shell-focus");
        });
    }

    private TextField champMotDePasseActif() {
        return motDePasseAffiche ? motDePasseVisibleField : motDePasseField;
    }

    private void ouvrirFenetrePrincipale() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) nomUtilisateurField.getScene().getWindow();
            Scene scene = new Scene(root, 1180, 740);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setMaximized(true);

        } catch (IOException e) {
            connexionButton.setDisable(false);
            connexionButton.setText("Se connecter");
            afficherErreur("Erreur lors du chargement de l'application.");
        }
    }
}