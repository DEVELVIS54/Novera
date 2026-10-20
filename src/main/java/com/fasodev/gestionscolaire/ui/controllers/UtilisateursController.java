package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Utilisateur;
import com.fasodev.gestionscolaire.services.AuthService;
import com.fasodev.gestionscolaire.services.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/** Écran Directeur : création et gestion des comptes Secrétaire. */
public class UtilisateursController implements Initializable {

    @FXML private TableView<Utilisateur> table;
    @FXML private TableColumn<Utilisateur, String> colNom;
    @FXML private TableColumn<Utilisateur, String> colRole;
    @FXML private TableColumn<Utilisateur, String> colEtat;
    @FXML private TextField nomField;
    @FXML private PasswordField motDePasseField;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();
    private final ObservableList<Utilisateur> donnees = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNomUtilisateur()));
        colRole.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isAdmin() ? "Directeur" : "Secrétaire"));
        colEtat.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isActif() ? "✅ Actif" : "⛔ Désactivé"));
        table.setItems(donnees);
        recharger();
    }

    @FXML
    public void onCreerClick() {
        try {
            authService.creerCompteSecretaire(nomField.getText(), motDePasseField.getText());
            nomField.clear();
            motDePasseField.clear();
            recharger();
            afficher("Compte Secrétaire créé.", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            afficher(e.getMessage(), true);
        }
    }

    @FXML
    public void onBasculerEtatClick() {
        Utilisateur choisi = table.getSelectionModel().getSelectedItem();
        if (choisi == null) {
            afficher("Sélectionne d'abord un compte.", true);
            return;
        }
        try {
            authService.changerActivation(choisi.getId(), !choisi.isActif());
            recharger();
            afficher(choisi.isActif() ? "Compte activé." : "Compte désactivé.", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            afficher(e.getMessage(), true);
        }
    }

    @FXML
    public void onReinitialiserClick() {
        Utilisateur choisi = table.getSelectionModel().getSelectedItem();
        if (choisi == null) {
            afficher("Sélectionne d'abord un compte.", true);
            return;
        }
        TextInputDialog dialogue = new TextInputDialog();
        dialogue.setTitle("Réinitialiser le mot de passe");
        dialogue.setHeaderText("Nouveau mot de passe pour " + choisi.getNomUtilisateur());
        dialogue.setContentText("Mot de passe (6 caractères minimum) :");

        Optional<String> saisie = dialogue.showAndWait();
        if (saisie.isEmpty()) return;

        try {
            authService.reinitialiserMotDePasse(choisi.getId(), saisie.get());
            afficher("Mot de passe modifié.", false);
        } catch (IllegalArgumentException e) {
            afficher(e.getMessage(), true);
        }
    }

    private void recharger() {
        donnees.setAll(authService.listerUtilisateurs());
    }

    private void afficher(String texte, boolean erreur) {
        messageLabel.setText(texte);
        messageLabel.setStyle(erreur ? "-fx-text-fill: #b91c1c;" : "-fx-text-fill: #166534;");
    }
}
