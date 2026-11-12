package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
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

/** Ã‰cran Directeur : annÃ©e scolaire active (consulter une annÃ©e passÃ©e, ouvrir la suivante). */
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
            afficher("Cette annÃ©e est dÃ©jÃ  l'annÃ©e active.", false);
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
            "Ouvrir l'annÃ©e scolaire " + suivante + " ?\n\n"
                + "Les notes, moyennes et calculs de " + recente + " sont conservÃ©s : "
                + "vous pourrez les consulter en revenant Ã  cette annÃ©e.",
            ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Nouvelle annÃ©e scolaire");
        confirmation.setHeaderText(null);

        Optional<ButtonType> reponse = confirmation.showAndWait();
        if (reponse.isPresent() && reponse.get() == ButtonType.OK) {
            changer(suivante);
        }
    }

    private void changer(String annee) {
        try {
            // La fenÃªtre principale rÃ©agit Ã  ce changement (en-tÃªte + retour au tableau de bord).
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