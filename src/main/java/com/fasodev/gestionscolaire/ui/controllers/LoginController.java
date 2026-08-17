package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Utilisateur;
import com.fasodev.gestionscolaire.services.AuthService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField nomUtilisateurField;
    @FXML private PasswordField motDePasseField;
    @FXML private Label erreurLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void onConnexionClick() {

        String nomUtilisateur = nomUtilisateurField.getText();
        String motDePasse = motDePasseField.getText();

        Utilisateur utilisateur = authService.connecter(nomUtilisateur, motDePasse);

        if (utilisateur == null) {
            afficherErreur("Nom d'utilisateur ou mot de passe incorrect.");
            return;
        }

        ouvrirFenetrePrincipale();
    }

    private void afficherErreur(String message) {
        erreurLabel.setText(message);
        erreurLabel.setVisible(true);
        erreurLabel.setManaged(true);
    }

    private void ouvrirFenetrePrincipale() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) nomUtilisateurField.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 650);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);

        } catch (IOException e) {
            afficherErreur("Erreur lors du chargement de l'application.");
        }
    }
}
