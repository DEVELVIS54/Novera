package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.FraisScolarite;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.FraisScolariteService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FraisScolariteController implements Initializable {

    @FXML private TableView<FraisScolarite> fraisTable;
    @FXML private TableColumn<FraisScolarite, String> colClasse;
    @FXML private TableColumn<FraisScolarite, String> colDescription;
    @FXML private TableColumn<FraisScolarite, String> colMontant;

    @FXML private Label formTitleLabel;
    @FXML private ComboBox<Classe> classeCombo;
    @FXML private TextField descriptionField;
    @FXML private TextField montantField;
    @FXML private Label erreurLabel;
    @FXML private Label messageLabel;
    @FXML private Button supprimerButton;

    private final FraisScolariteService fraisService = new FraisScolariteService();
    private final ClasseService classeService = new ClasseService();

    private final ObservableList<FraisScolarite> fraisData = FXCollections.observableArrayList();
    private FraisScolarite fraisEnEdition = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colClasse.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getClasseNom()));
        colDescription.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getDescription()));
        colMontant.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                String.format("%,.0f", cell.getValue().getMontant())));

        fraisTable.setItems(fraisData);

        fraisTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau != null) chargerPourEdition(nouveau);
        });

        List<Classe> classes = classeService.listerToutes();
        classeCombo.setItems(FXCollections.observableArrayList(classes));

        rafraichirListe();
        reinitialiserFormulaire();
    }

    private void rafraichirListe() {
        fraisData.setAll(fraisService.listerTous());
    }

    @FXML
    public void onNouveauClick() {
        fraisTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    private void chargerPourEdition(FraisScolarite frais) {
        fraisEnEdition = frais;
        formTitleLabel.setText("Modifier le frais");

        classeCombo.getItems().stream()
            .filter(c -> c.getId() == frais.getClasseId())
            .findFirst()
            .ifPresent(classeCombo::setValue);

        descriptionField.setText(frais.getDescription());
        montantField.setText(String.valueOf(frais.getMontant()));

        masquerErreur();
        supprimerButton.setVisible(true);
        supprimerButton.setManaged(true);
    }

    private void reinitialiserFormulaire() {
        fraisEnEdition = null;
        formTitleLabel.setText("Nouveau frais");

        classeCombo.setValue(null);
        descriptionField.clear();
        montantField.clear();

        masquerErreur();
        supprimerButton.setVisible(false);
        supprimerButton.setManaged(false);
    }

    @FXML
    public void onEnregistrerClick() {

        Classe classe = classeCombo.getValue();
        FraisScolarite frais = (fraisEnEdition != null) ? fraisEnEdition : new FraisScolarite();

        frais.setClasseId(classe != null ? classe.getId() : 0);
        frais.setDescription(descriptionField.getText());

        try {
            frais.setMontant(Double.parseDouble(montantField.getText().replace(",", ".")));
        } catch (NumberFormatException e) {
            afficherErreur("Le montant doit être un nombre valide.");
            return;
        }

        try {
            if (fraisEnEdition != null) {
                fraisService.modifier(frais);
                afficherMessage("✅ Frais modifié.");
            } else {
                fraisService.creer(frais);
                afficherMessage("✅ Frais créé.");
            }

            rafraichirListe();
            fraisTable.getSelectionModel().clearSelection();
            reinitialiserFormulaire();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onAnnulerClick() {
        fraisTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    @FXML
    public void onSupprimerClick() {

        if (fraisEnEdition == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Supprimer ce frais ?");

        confirmation.showAndWait().ifPresent(reponse -> {
            if (reponse == ButtonType.OK) {
                fraisService.supprimer(fraisEnEdition.getId());
                afficherMessage("✅ Frais supprimé.");
                rafraichirListe();
                reinitialiserFormulaire();
            }
        });
    }

    private void afficherErreur(String message) {
        erreurLabel.setText(message);
        erreurLabel.setVisible(true);
        erreurLabel.setManaged(true);
        messageLabel.setText("");
    }

    private void masquerErreur() {
        erreurLabel.setVisible(false);
        erreurLabel.setManaged(false);
    }

    private void afficherMessage(String message) {
        messageLabel.setText(message);
        masquerErreur();
    }
}
