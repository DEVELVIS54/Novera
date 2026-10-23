package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.services.AuthService;
import com.fasodev.gestionscolaire.services.ForceMotDePasse;
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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/** Première configuration : nom de l'établissement et compte Directeur, dans le thème de Novera. */
public class SetupController implements Initializable {

    @FXML private Pane fond;
    @FXML private HBox carte;
    @FXML private StackPane illustrationHolder;

    @FXML private HBox coqueEcole;
    @FXML private HBox coqueUtilisateur;
    @FXML private HBox coqueMotDePasse;
    @FXML private HBox coqueConfirmation;
    @FXML private StackPane iconeEcole;
    @FXML private StackPane iconeUtilisateur;
    @FXML private StackPane iconeCadenas;
    @FXML private StackPane iconeConfirmation;

    @FXML private TextField nomEcoleField;
    @FXML private TextField nomUtilisateurField;
    @FXML private PasswordField motDePasseField;
    @FXML private TextField motDePasseVisibleField;
    @FXML private PasswordField confirmerMotDePasseField;
    @FXML private Button oeilButton;
    @FXML private HBox forceBarre;
    @FXML private Label forceLabel;
    @FXML private Label confirmationLabel;
    @FXML private Label erreurLabel;
    @FXML private Button terminerButton;

    private final AuthService authService = new AuthService();
    private final ConfigurationEcoleRepository configRepository = new ConfigurationEcoleRepository();
    private boolean motDePasseAffiche = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        FondNovera.appliquer(fond);
        illustrationHolder.getChildren().add(DashboardView.decor());

        iconeEcole.getChildren().add(Icons.icone("school", 18));
        iconeUtilisateur.getChildren().add(Icons.icone("user", 18));
        iconeCadenas.getChildren().add(Icons.icone("lock", 18));
        iconeConfirmation.getChildren().add(Icons.icone("lock", 18));
        oeilButton.setGraphic(Icons.icone("eye", 20, 14));
        oeilButton.setFocusTraversable(false);

        // Indicateur de solidité : trois segments
        for (int i = 0; i < 3; i++) {
            Region segment = new Region();
            segment.getStyleClass().add("lg-seg");
            HBox.setHgrow(segment, javafx.scene.layout.Priority.ALWAYS);
            segment.setMaxWidth(Double.MAX_VALUE);
            segment.setPrefHeight(6);
            forceBarre.getChildren().add(segment);
        }
        mettreAJourForce("");

        motDePasseVisibleField.textProperty().bindBidirectional(motDePasseField.textProperty());

        suivreFocus(nomEcoleField, coqueEcole);
        suivreFocus(nomUtilisateurField, coqueUtilisateur);
        suivreFocus(motDePasseField, coqueMotDePasse);
        suivreFocus(motDePasseVisibleField, coqueMotDePasse);
        suivreFocus(confirmerMotDePasseField, coqueConfirmation);

        motDePasseField.textProperty().addListener((o, a, b) -> {
            mettreAJourForce(b);
            mettreAJourConfirmation();
            masquerErreur();
        });
        confirmerMotDePasseField.textProperty().addListener((o, a, b) -> {
            mettreAJourConfirmation();
            masquerErreur();
        });
        nomEcoleField.textProperty().addListener((o, a, b) -> masquerErreur());
        nomUtilisateurField.textProperty().addListener((o, a, b) -> masquerErreur());

        nomEcoleField.setOnAction(e -> nomUtilisateurField.requestFocus());
        nomUtilisateurField.setOnAction(e -> champMotDePasseActif().requestFocus());
        motDePasseField.setOnAction(e -> confirmerMotDePasseField.requestFocus());
        motDePasseVisibleField.setOnAction(e -> confirmerMotDePasseField.requestFocus());
        confirmerMotDePasseField.setOnAction(e -> onTerminerClick());

        Platform.runLater(nomEcoleField::requestFocus);
    }

    @FXML
    public void onTerminerClick() {
        String nomEcole = nomEcoleField.getText() == null ? "" : nomEcoleField.getText().trim();
        String nomUtilisateur = nomUtilisateurField.getText() == null ? "" : nomUtilisateurField.getText().trim();
        String motDePasse = motDePasseField.getText() == null ? "" : motDePasseField.getText();
        String confirmation = confirmerMotDePasseField.getText() == null ? "" : confirmerMotDePasseField.getText();

        if (nomEcole.isEmpty()) {
            erreur("Le nom de l'établissement est requis.", coqueEcole, nomEcoleField);
            return;
        }
        if (!motDePasse.equals(confirmation)) {
            // Quand la confirmation est remplie, l'indicateur sous le champ explique déjà le problème
            String message = confirmation.isEmpty() ? "Confirmez le mot de passe." : null;
            erreur(message, coqueConfirmation, confirmerMotDePasseField);
            return;
        }

        terminerButton.setDisable(true);
        terminerButton.setText("Configuration…");
        try {
            authService.creerPremierCompteAdmin(nomUtilisateur, motDePasse);
            configRepository.mettreAJourNom(nomEcole);

            // Au prochain lancement, l'écran de connexion proposera déjà ce compte
            Preferences.userNodeForPackage(LoginController.class).put("dernierUtilisateur", nomUtilisateur);

            ouvrirFenetrePrincipale();

        } catch (IllegalArgumentException | IllegalStateException e) {
            terminerButton.setDisable(false);
            terminerButton.setText("Terminer la configuration");
            erreur(e.getMessage(), coqueUtilisateur, nomUtilisateurField);
        } catch (RuntimeException e) {
            terminerButton.setDisable(false);
            terminerButton.setText("Terminer la configuration");
            erreur("Erreur technique : " + e.getMessage(), null, null);
        }
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

    // ------------------------------------------------------------------ indicateurs

    private void mettreAJourForce(String motDePasse) {
        int force = ForceMotDePasse.evaluer(motDePasse);
        String classe = switch (force) {
            case ForceMotDePasse.FAIBLE -> "lg-seg-faible";
            case ForceMotDePasse.MOYENNE -> "lg-seg-moyen";
            case ForceMotDePasse.FORTE -> "lg-seg-fort";
            default -> null;
        };
        for (int i = 0; i < forceBarre.getChildren().size(); i++) {
            var segment = forceBarre.getChildren().get(i);
            segment.getStyleClass().removeAll("lg-seg-faible", "lg-seg-moyen", "lg-seg-fort");
            if (classe != null && i < force) segment.getStyleClass().add(classe);
        }
        forceLabel.setText(ForceMotDePasse.libelle(force));
    }

    private void mettreAJourConfirmation() {
        String confirmation = confirmerMotDePasseField.getText();
        boolean vide = confirmation == null || confirmation.isEmpty();
        confirmationLabel.setVisible(!vide);
        confirmationLabel.setManaged(!vide);
        if (vide) return;

        boolean identiques = confirmation.equals(motDePasseField.getText());
        confirmationLabel.setText(identiques
            ? "Les mots de passe correspondent"
            : "Les mots de passe ne correspondent pas");
        confirmationLabel.getStyleClass().removeAll("lg-hint-ok", "lg-hint-ko");
        confirmationLabel.getStyleClass().add(identiques ? "lg-hint-ok" : "lg-hint-ko");
    }

    // ------------------------------------------------------------------ erreurs et détails

    private void erreur(String message, HBox coque, TextField champ) {
        if (message != null) {
            erreurLabel.setText(message);
            erreurLabel.setVisible(true);
        }
        if (coque != null) coque.getStyleClass().add("lg-shell-erreur");
        if (champ != null) champ.requestFocus();
        secouerCarte();
    }

    private void masquerErreur() {
        erreurLabel.setVisible(false);
        for (HBox coque : new HBox[] {coqueEcole, coqueUtilisateur, coqueMotDePasse, coqueConfirmation}) {
            coque.getStyleClass().remove("lg-shell-erreur");
        }
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

            Stage stage = (Stage) nomEcoleField.getScene().getWindow();
            Scene scene = new Scene(root, 1180, 740);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
            stage.setMaximized(true);

        } catch (IOException e) {
            terminerButton.setDisable(false);
            terminerButton.setText("Terminer la configuration");
            erreur("Erreur lors du chargement de l'application.", null, null);
        }
    }
}