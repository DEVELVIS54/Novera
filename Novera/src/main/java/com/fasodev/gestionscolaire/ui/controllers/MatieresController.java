package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Matiere;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.MatiereService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MatieresController implements Initializable {

    @FXML private ComboBox<Classe> filtreClasseCombo;
    @FXML private TableView<Matiere> matieresTable;
    @FXML private TableColumn<Matiere, String> colNom;
    @FXML private TableColumn<Matiere, String> colProfesseur;
    @FXML private TableColumn<Matiere, String> colClasse;
    @FXML private TableColumn<Matiere, String> colCoefficient;
    @FXML private TableColumn<Matiere, String> colBareme;

    @FXML private Label formTitleLabel;
    @FXML private TextField nomField;
    @FXML private TextField professeurField;
    @FXML private ComboBox<Classe> classeCombo;
    @FXML private TextField coefficientField;
    @FXML private TextField baremeMinField;
    @FXML private TextField baremeMaxField;
    @FXML private Label erreurLabel;
    @FXML private Label messageLabel;
    @FXML private Button supprimerButton;

    private final MatiereService matiereService = new MatiereService();
    private final ClasseService classeService = new ClasseService();

    private final ObservableList<Matiere> matieresData = FXCollections.observableArrayList();
    private Matiere matiereEnEdition = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colNom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getNom()));
        colProfesseur.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getNomProfesseur() != null ? cell.getValue().getNomProfesseur() : "—"));
        colClasse.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getClasseNom()));
        colCoefficient.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(cell.getValue().getCoefficient())));
        colBareme.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getBaremeMin() + " - " + cell.getValue().getBaremeMax()));

        matieresTable.setItems(matieresData);

        matieresTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau != null) {
                chargerPourEdition(nouveau);
            }
        });

        List<Classe> classes = classeService.listerToutes();
        classeCombo.setItems(FXCollections.observableArrayList(classes));
        filtreClasseCombo.setItems(FXCollections.observableArrayList(classes));
        filtreClasseCombo.setOnAction(e -> appliquerFiltre());

        rafraichirListe();
        reinitialiserFormulaire();
    }

    private void appliquerFiltre() {
        Classe filtre = filtreClasseCombo.getValue();
        if (filtre == null) {
            matieresData.setAll(matiereService.listerToutes());
        } else {
            matieresData.setAll(matiereService.listerParClasse(filtre.getId()));
        }
    }

    private void rafraichirListe() {
        appliquerFiltre();
    }

    @FXML
    public void onNouvelleMatiereClick() {
        matieresTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    private void chargerPourEdition(Matiere matiere) {
        matiereEnEdition = matiere;
        formTitleLabel.setText("Modifier : " + matiere.getNom());

        nomField.setText(matiere.getNom());
        professeurField.setText(matiere.getNomProfesseur());
        coefficientField.setText(String.valueOf(matiere.getCoefficient()));
        baremeMinField.setText(String.valueOf(matiere.getBaremeMin()));
        baremeMaxField.setText(String.valueOf(matiere.getBaremeMax()));

        classeCombo.getItems().stream()
            .filter(c -> c.getId() == matiere.getClasseId())
            .findFirst()
            .ifPresent(classeCombo::setValue);

        masquerErreur();

        supprimerButton.setVisible(true);
        supprimerButton.setManaged(true);
    }

    private void reinitialiserFormulaire() {
        matiereEnEdition = null;
        formTitleLabel.setText("Nouvelle matière");

        nomField.clear();
        professeurField.clear();
        classeCombo.setValue(null);
        coefficientField.setText("1");
        baremeMinField.setText("0");
        baremeMaxField.setText("20");

        masquerErreur();

        supprimerButton.setVisible(false);
        supprimerButton.setManaged(false);
    }

    @FXML
    public void onEnregistrerClick() {

        Classe classeSelectionnee = classeCombo.getValue();
        Matiere matiere = (matiereEnEdition != null) ? matiereEnEdition : new Matiere();

        matiere.setNom(nomField.getText());
        matiere.setNomProfesseur(professeurField.getText());
        matiere.setClasseId(classeSelectionnee != null ? classeSelectionnee.getId() : 0);

        try {
            matiere.setCoefficient(Double.parseDouble(coefficientField.getText().replace(",", ".")));
            matiere.setBaremeMin(Double.parseDouble(baremeMinField.getText().replace(",", ".")));
            matiere.setBaremeMax(Double.parseDouble(baremeMaxField.getText().replace(",", ".")));
        } catch (NumberFormatException e) {
            afficherErreur("Coefficient et barèmes doivent être des nombres valides.");
            return;
        }

        try {
            if (matiereEnEdition != null) {
                matiereService.modifier(matiere);
                afficherMessage("✅ Matière \"" + matiere.getNom() + "\" modifiée.");
            } else {
                matiereService.creer(matiere);
                afficherMessage("✅ Matière \"" + matiere.getNom() + "\" créée.");
            }

            rafraichirListe();
            matieresTable.getSelectionModel().clearSelection();
            reinitialiserFormulaire();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onAnnulerClick() {
        matieresTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    @FXML
    public void onSupprimerClick() {

        if (matiereEnEdition == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Supprimer la matière \"" + matiereEnEdition.getNom() + "\" ?");

        confirmation.showAndWait().ifPresent(reponse -> {
            if (reponse == ButtonType.OK) {
                try {
                    matiereService.supprimer(matiereEnEdition.getId());
                    afficherMessage("✅ Matière supprimée.");
                    rafraichirListe();
                    reinitialiserFormulaire();
                } catch (IllegalStateException e) {
                    afficherErreur(e.getMessage());
                }
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
