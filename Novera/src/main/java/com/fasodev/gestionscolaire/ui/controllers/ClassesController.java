package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.services.ClasseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class ClassesController implements Initializable {

    @FXML private TableView<Classe> classesTable;
    @FXML private TableColumn<Classe, String> colNom;
    @FXML private TableColumn<Classe, String> colNiveau;
    @FXML private TableColumn<Classe, String> colExamen;
    @FXML private TableColumn<Classe, String> colFinParcours;

    @FXML private Label formTitleLabel;
    @FXML private TextField nomField;
    @FXML private TextField niveauField;
    @FXML private CheckBox examenCheckBox;
    @FXML private VBox examenDetailsBox;
    @FXML private TextField nomExamenField;
    @FXML private CheckBox finParcoursCheckBox;
    @FXML private Label erreurLabel;
    @FXML private Label messageLabel;
    @FXML private Button supprimerButton;

    private final ClasseService classeService = new ClasseService();
    private final ObservableList<Classe> classesData = FXCollections.observableArrayList();

    private Classe classeEnEdition = null; // null = mode création

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));

        colExamen.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isEstClasseExamen()
                    ? "✅ " + cell.getValue().getNomExamen()
                    : "—"
            )
        );

        colFinParcours.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isFinDeParcours() ? "✅ Oui" : "—"
            )
        );

        classesTable.setItems(classesData);

        classesTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau != null) {
                chargerPourEdition(nouveau);
            }
        });

        rafraichirListe();
        reinitialiserFormulaire();
    }

    private void rafraichirListe() {
        classesData.setAll(classeService.listerToutes());
    }

    @FXML
    public void onExamenCheckBoxChange() {
        boolean coche = examenCheckBox.isSelected();
        examenDetailsBox.setVisible(coche);
        examenDetailsBox.setManaged(coche);
    }

    @FXML
    public void onNouvelleClasseClick() {
        classesTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    private void chargerPourEdition(Classe classe) {
        classeEnEdition = classe;
        formTitleLabel.setText("Modifier : " + classe.getNom());

        nomField.setText(classe.getNom());
        niveauField.setText(classe.getNiveau());
        examenCheckBox.setSelected(classe.isEstClasseExamen());
        nomExamenField.setText(classe.getNomExamen());
        finParcoursCheckBox.setSelected(classe.isFinDeParcours());

        onExamenCheckBoxChange(); // met à jour la visibilité des détails examen
        masquerErreur();

        supprimerButton.setVisible(true);
        supprimerButton.setManaged(true);
    }

    private void reinitialiserFormulaire() {
        classeEnEdition = null;
        formTitleLabel.setText("Nouvelle classe");

        nomField.clear();
        niveauField.clear();
        examenCheckBox.setSelected(false);
        nomExamenField.clear();
        finParcoursCheckBox.setSelected(false);
        examenDetailsBox.setVisible(false);
        examenDetailsBox.setManaged(false);

        masquerErreur();

        supprimerButton.setVisible(false);
        supprimerButton.setManaged(false);
    }

    @FXML
    public void onEnregistrerClick() {

        Classe classe = (classeEnEdition != null) ? classeEnEdition : new Classe();

        classe.setNom(nomField.getText());
        classe.setNiveau(niveauField.getText());
        classe.setEstClasseExamen(examenCheckBox.isSelected());
        classe.setNomExamen(examenCheckBox.isSelected() ? nomExamenField.getText() : null);
        classe.setFinDeParcours(examenCheckBox.isSelected() && finParcoursCheckBox.isSelected());

        try {
            if (classeEnEdition != null) {
                classeService.modifier(classe);
                afficherMessage("✅ Classe \"" + classe.getNom() + "\" modifiée.");
            } else {
                classeService.creer(classe);
                afficherMessage("✅ Classe \"" + classe.getNom() + "\" créée.");
            }

            rafraichirListe();
            classesTable.getSelectionModel().clearSelection();
            reinitialiserFormulaire();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onAnnulerClick() {
        classesTable.getSelectionModel().clearSelection();
        reinitialiserFormulaire();
    }

    @FXML
    public void onSupprimerClick() {

        if (classeEnEdition == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer la suppression");
        confirmation.setHeaderText(null);
        confirmation.setContentText(
            "Supprimer la classe \"" + classeEnEdition.getNom() + "\" ?"
        );

        confirmation.showAndWait().ifPresent(reponse -> {
            if (reponse == ButtonType.OK) {
                try {
                    classeService.supprimer(classeEnEdition.getId());
                    afficherMessage("✅ Classe supprimée.");
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
