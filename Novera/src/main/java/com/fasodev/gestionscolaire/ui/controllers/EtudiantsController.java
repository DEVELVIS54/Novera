package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.EtudiantService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class EtudiantsController implements Initializable {

    @FXML private ComboBox<Classe> filtreClasseCombo;
    @FXML private TableView<Etudiant> etudiantsTable;
    @FXML private TableColumn<Etudiant, String> colNom;
    @FXML private TableColumn<Etudiant, String> colPrenom;
    @FXML private TableColumn<Etudiant, String> colClasse;
    @FXML private TableColumn<Etudiant, String> colMatricule;
    @FXML private TableColumn<Etudiant, String> colStatut;
    @FXML private TableColumn<Etudiant, String> colSubvention;

    @FXML private Label formTitleLabel;
    @FXML private TextField prenomField;
    @FXML private TextField nomField;
    @FXML private DatePicker dateNaissanceField;
    @FXML private ComboBox<Classe> classeCombo;
    @FXML private TextField matriculeField;
    @FXML private CheckBox affecteEtatCheckBox;
    @FXML private VBox palierBox;
    @FXML private ComboBox<String> palierCombo;
    @FXML private Label erreurLabel;
    @FXML private Label messageLabel;

    @FXML private VBox departBox;
    @FXML private DatePicker dateDepartField;
    @FXML private TextArea raisonDepartField;

    private final EtudiantService etudiantService = new EtudiantService();
    private final ClasseService classeService = new ClasseService();

    private final ObservableList<Etudiant> etudiantsData = FXCollections.observableArrayList();
    private Etudiant etudiantEnEdition = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colNom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getNom()));
        colPrenom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getPrenom()));
        colClasse.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getClasseNom()));
        colMatricule.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getMatricule()));
        colStatut.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isActif() ? "✅ Actif" : "🚪 Parti"
            ));
        colSubvention.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isAffecteEtat()
                    ? "✅ " + cell.getValue().getPalierSubvention()
                    : "—"
            ));

        etudiantsTable.setItems(etudiantsData);

        etudiantsTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau != null) {
                chargerPourEdition(nouveau);
            }
        });

        palierCombo.setItems(FXCollections.observableArrayList("CEP", "BEPC"));

        List<Classe> classes = classeService.listerToutes();
        classeCombo.setItems(FXCollections.observableArrayList(classes));

        ObservableList<Classe> classesAvecToutes = FXCollections.observableArrayList(classes);
        filtreClasseCombo.setItems(classesAvecToutes);
        filtreClasseCombo.setOnAction(e -> appliquerFiltre());

        rafraichirListe();
        reinitialiserFormulaire();
    }

    private void appliquerFiltre() {
        Classe filtre = filtreClasseCombo.getValue();
        if (filtre == null) {
            etudiantsData.setAll(etudiantService.listerTous());
        } else {
            etudiantsData.setAll(etudiantService.listerParClasse(filtre.getId()));
        }
    }

    private void rafraichirListe() {
        appliquerFiltre();
    }

    @FXML
    public void onAffecteEtatChange() {
        boolean coche = affecteEtatCheckBox.isSelected();
        palierBox.setVisible(coche);
        palierBox.setManaged(coche);
    }

    @FXML
    public void onNouvelEtudiantClick() {
        etudiantsTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    private void chargerPourEdition(Etudiant etudiant) {
        etudiantEnEdition = etudiant;
        formTitleLabel.setText("Modifier : " + etudiant.getNomComplet());

        prenomField.setText(etudiant.getPrenom());
        nomField.setText(etudiant.getNom());
        dateNaissanceField.setValue(etudiant.getDateNaissance());
        matriculeField.setText(etudiant.getMatricule());

        classeCombo.getItems().stream()
            .filter(c -> c.getId() == etudiant.getClasseId())
            .findFirst()
            .ifPresent(classeCombo::setValue);

        affecteEtatCheckBox.setSelected(etudiant.isAffecteEtat());
        palierCombo.setValue(etudiant.getPalierSubvention());
        onAffecteEtatChange();

        masquerErreur();

        // Départ possible uniquement en mode édition, et si actif
        departBox.setVisible(etudiant.isActif());
        departBox.setManaged(etudiant.isActif());
        dateDepartField.setValue(null);
        raisonDepartField.clear();
    }

    private void reinitialiserFormulaire() {
        etudiantEnEdition = null;
        formTitleLabel.setText("Nouvel étudiant");

        prenomField.clear();
        nomField.clear();
        dateNaissanceField.setValue(null);
        classeCombo.setValue(null);
        matriculeField.clear();
        affecteEtatCheckBox.setSelected(false);
        palierCombo.setValue(null);
        onAffecteEtatChange();

        masquerErreur();

        departBox.setVisible(false);
        departBox.setManaged(false);
    }

    @FXML
    public void onEnregistrerClick() {

        Classe classeSelectionnee = classeCombo.getValue();

        Etudiant etudiant = (etudiantEnEdition != null) ? etudiantEnEdition : new Etudiant();

        etudiant.setPrenom(prenomField.getText());
        etudiant.setNom(nomField.getText());
        etudiant.setDateNaissance(dateNaissanceField.getValue());
        etudiant.setClasseId(classeSelectionnee != null ? classeSelectionnee.getId() : 0);
        etudiant.setMatricule(matriculeField.getText());
        etudiant.setAffecteEtat(affecteEtatCheckBox.isSelected());
        etudiant.setPalierSubvention(affecteEtatCheckBox.isSelected() ? palierCombo.getValue() : null);

        try {
            if (etudiantEnEdition != null) {
                etudiantService.modifier(etudiant);
                afficherMessage("✅ Étudiant \"" + etudiant.getNomComplet() + "\" modifié.");
            } else {
                etudiantService.creer(etudiant);
                afficherMessage("✅ Étudiant \"" + etudiant.getNomComplet() +
                    "\" créé (matricule : " + etudiant.getMatricule() + ").");
            }

            rafraichirListe();
            etudiantsTable.getSelectionModel().clearSelection();
            reinitialiserFormulaire();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onAnnulerClick() {
        etudiantsTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    @FXML
    public void onMarquerPartiClick() {

        if (etudiantEnEdition == null) return;

        LocalDate dateDepart = dateDepartField.getValue();
        if (dateDepart == null) {
            afficherErreur("Veuillez indiquer la date de départ.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer le départ");
        confirmation.setHeaderText(null);
        confirmation.setContentText(
            "Marquer " + etudiantEnEdition.getNomComplet() + " comme parti de l'établissement ?\n" +
            "Son historique (notes, paiements) sera conservé."
        );

        confirmation.showAndWait().ifPresent(reponse -> {
            if (reponse == ButtonType.OK) {
                etudiantService.marquerCommeParti(
                    etudiantEnEdition.getId(), dateDepart, raisonDepartField.getText()
                );
                afficherMessage("✅ " + etudiantEnEdition.getNomComplet() + " marqué comme parti.");
                rafraichirListe();
                etudiantsTable.getSelectionModel().clearSelection();
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
