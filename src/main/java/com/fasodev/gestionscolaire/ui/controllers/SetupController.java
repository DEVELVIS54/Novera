package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
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

public class SetupController {

    @FXML private TextField nomEcoleField;
    @FXML private TextField nomUtilisateurField;
    @FXML private PasswordField motDePasseField;
    @FXML private PasswordField confirmerMotDePasseField;
    @FXML private Label erreurLabel;

    private final AuthService authService = new AuthService();
    private final ConfigurationEcoleRepository configRepository = new ConfigurationEcoleRepository();

    @FXML
    public void onTerminerClick() {

        String nomEcole = nomEcoleField.getText();
        String nomUtilisateur = nomUtilisateurField.getText();
        String motDePasse = motDePasseField.getText();
        String confirmation = confirmerMotDePasseField.getText();

        if (nomEcole == null || nomEcole.isBlank()) {
            afficherErreur("Le nom de l'établissement est requis.");
            return;
        }

        if (!motDePasse.equals(confirmation)) {
            afficherErreur("Les mots de passe ne correspondent pas.");
            return;
        }

        try {
            authService.creerPremierCompteAdmin(nomUtilisateur, motDePasse);
            configRepository.mettreAJourNom(nomEcole.trim());

            ouvrirFenetrePrincipale();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
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

            Stage stage = (Stage) nomEcoleField.getScene().getWindow();
            Scene scene = new Scene(root, 1000, 650);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);

        } catch (IOException e) {
            afficherErreur("Erreur lors du chargement de l'application.");
        }
    }
}
