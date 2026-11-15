package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.FraisScolariteRepository;
import com.fasodev.gestionscolaire.ui.AnneeActive;
import com.fasodev.gestionscolaire.utils.AnneeScolaire;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/** Écran Directeur : année scolaire active (consulter une année passée, ouvrir la suivante). */
public class AnneeScolaireController implements Initializable {

    @FXML private Label anneeActiveLabel;
    @FXML private ComboBox<String> anneeCombo;
    @FXML private Label messageLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String active = ConfigurationEcoleRepository.obtenirAnneeActive();
        anneeActiveLabel.setText(active);
        anneeCombo.getItems().setAll(ConfigurationEcoleRepository.anneesDisponibles());
        anneeCombo.setValue(active);
    }

    @FXML
    public void onActiverClick() {
        String choisie = anneeCombo.getValue();
        if (choisie == null || choisie.equals(ConfigurationEcoleRepository.obtenirAnneeActive())) {
            afficher("Cette année est déjà l'année active.", false);
            return;
        }
        changer(choisie);
    }

    @FXML
    public void onOuvrirSuivanteClick() {
        List<String> annees = ConfigurationEcoleRepository.anneesDisponibles();
        String recente = annees.get(0);
        String suivante = AnneeScolaire.suivante(recente);

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
            "Ouvrir l'année scolaire " + suivante + " ?\n\n"
                + "Les notes, moyennes et calculs de " + recente + " sont conservés : "
                + "vous pourrez les consulter en revenant à cette année.",
            ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Nouvelle année scolaire");
        confirmation.setHeaderText(null);
        confirmation.setContentText(confirmation.getContentText()
            + "\n\nLes frais de scolarité de " + recente + " seront recopiés pour " + suivante + " (modifiables).");

        Optional<ButtonType> reponse = confirmation.showAndWait();
        if (reponse.isPresent() && reponse.get() == ButtonType.OK) {
            new FraisScolariteRepository().copierVersAnnee(recente, suivante);
            changer(suivante);
        }
    }

    private void changer(String annee) {
        try {
            // La fenêtre principale réagit à ce changement (en-tête + retour au tableau de bord).
            AnneeActive.definir(annee);
        } catch (RuntimeException e) {
            afficher(e.getMessage(), true);
        }
    }

    private void afficher(String texte, boolean erreur) {
        messageLabel.setText(texte);
        messageLabel.setStyle(erreur ? "-fx-text-fill: #b91c1c;" : "-fx-text-fill: #166534;");
    }
}