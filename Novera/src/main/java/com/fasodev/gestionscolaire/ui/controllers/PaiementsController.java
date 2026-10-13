package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Paiement;
import com.fasodev.gestionscolaire.models.SoldeEtudiant;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.EtudiantService;
import com.fasodev.gestionscolaire.services.PaiementService;
import com.fasodev.gestionscolaire.services.RecuPdfService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class PaiementsController implements Initializable {

    @FXML private ComboBox<Classe> classeCombo;
    @FXML private ComboBox<Etudiant> etudiantCombo;

    @FXML private VBox soldeBox;
    @FXML private Label fraisNormalLabel;
    @FXML private Label subventionLabel;
    @FXML private Label fraisReelLabel;
    @FXML private Label totalPayeLabel;
    @FXML private Label soldeLabel;

    @FXML private TableView<Paiement> historiqueTable;
    @FXML private TableColumn<Paiement, String> colDate;
    @FXML private TableColumn<Paiement, String> colMontant;
    @FXML private TableColumn<Paiement, String> colStatut;
    @FXML private TableColumn<Paiement, String> colNotes;

    @FXML private TextField montantField;
    @FXML private DatePicker dateField;
    @FXML private TextField notesField;
    @FXML private Label erreurLabel;
    @FXML private Label messageLabel;
    @FXML private Button imprimerRecuButton;

    private final PaiementService paiementService = new PaiementService();
    private final ClasseService classeService = new ClasseService();
    private final EtudiantService etudiantService = new EtudiantService();
    private final RecuPdfService recuPdfService = new RecuPdfService();

    private final ObservableList<Paiement> historiqueData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colDate.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getDatePaiement() != null ? cell.getValue().getDatePaiement().toString() : ""));
        colMontant.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                String.format("%,.0f", cell.getValue().getMontant())));
        colStatut.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(formaterStatut(cell.getValue().getStatut())));
        colNotes.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getNotes() != null ? cell.getValue().getNotes() : ""));

        historiqueTable.setItems(historiqueData);

        historiqueTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            imprimerRecuButton.setDisable(nouveau == null);
        });

        classeCombo.setItems(FXCollections.observableArrayList(classeService.listerToutes()));
        classeCombo.setOnAction(e -> chargerEtudiantsDeLaClasse());

        etudiantCombo.setOnAction(e -> rafraichirSolde());

        dateField.setValue(LocalDate.now());

        masquerSolde();
    }

    private void chargerEtudiantsDeLaClasse() {
        Classe classe = classeCombo.getValue();
        if (classe == null) {
            etudiantCombo.setItems(FXCollections.observableArrayList());
            return;
        }

        List<Etudiant> etudiants = etudiantService.listerParClasse(classe.getId());
        etudiantCombo.setItems(FXCollections.observableArrayList(etudiants));
        etudiantCombo.setValue(null);
        masquerSolde();
    }

    private void rafraichirSolde() {

        Etudiant etudiant = etudiantCombo.getValue();
        if (etudiant == null) {
            masquerSolde();
            return;
        }

        SoldeEtudiant solde = paiementService.calculerSolde(etudiant.getId());

        fraisNormalLabel.setText("Frais normal : " + String.format("%,.0f", solde.getFraisNormal()) + " FCFA");

        if (solde.isSubventionAppliquee()) {
            subventionLabel.setText("✅ Subvention État appliquée : -" +
                String.format("%.0f", solde.getPourcentageReduction()) + "%");
            subventionLabel.setVisible(true);
            subventionLabel.setManaged(true);
        } else {
            subventionLabel.setText("");
            subventionLabel.setVisible(false);
            subventionLabel.setManaged(false);
        }

        fraisReelLabel.setText("Frais à payer : " + String.format("%,.0f", solde.getFraisReel()) + " FCFA");
        totalPayeLabel.setText("Total déjà payé : " + String.format("%,.0f", solde.getTotalPaye()) + " FCFA");

        double soldeRestant = solde.getSolde();
        String texteSolde = soldeRestant <= 0
            ? "✅ Soldé"
            : "⚠️ Solde restant : " + String.format("%,.0f", soldeRestant) + " FCFA";
        soldeLabel.setText(texteSolde);

        soldeBox.setVisible(true);
        soldeBox.setManaged(true);

        historiqueData.setAll(paiementService.historique(etudiant.getId()));

        masquerErreur();
    }

    private void masquerSolde() {
        soldeBox.setVisible(false);
        soldeBox.setManaged(false);
        historiqueData.clear();
        imprimerRecuButton.setDisable(true);
    }

    @FXML
    public void onEnregistrerClick() {

        Etudiant etudiant = etudiantCombo.getValue();
        if (etudiant == null) {
            afficherErreur("Veuillez sélectionner un étudiant.");
            return;
        }

        double montant;
        try {
            montant = Double.parseDouble(montantField.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            afficherErreur("Le montant doit être un nombre valide.");
            return;
        }

        try {
            paiementService.enregistrerPaiement(
                etudiant.getId(), montant, dateField.getValue(), notesField.getText()
            );

            afficherMessage("✅ Paiement de " + String.format("%,.0f", montant) + " FCFA enregistré.");

            montantField.clear();
            notesField.clear();
            dateField.setValue(LocalDate.now());

            rafraichirSolde();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onImprimerRecuClick() {

        Etudiant etudiant = etudiantCombo.getValue();
        Paiement paiementSelectionne = historiqueTable.getSelectionModel().getSelectedItem();

        if (etudiant == null || paiementSelectionne == null) {
            afficherErreur("Veuillez sélectionner un paiement dans l'historique.");
            return;
        }

        try {
            SoldeEtudiant soldeActuel = paiementService.calculerSolde(etudiant.getId());

            File fichier = recuPdfService.genererRecu(paiementSelectionne, etudiant, soldeActuel);

            afficherMessage("✅ Reçu généré : " + fichier.getName());
            recuPdfService.ouvrirFichier(fichier);

        } catch (RuntimeException e) {
            afficherErreur("Erreur lors de la génération du reçu : " + e.getMessage());
        }
    }

    private String formaterStatut(String statut) {
        if (statut == null) return "";
        return switch (statut) {
            case "payé" -> "✅ Soldé après ce paiement";
            case "partiellement" -> "⏳ Partiel";
            case "impayé" -> "❌ Impayé";
            default -> statut;
        };
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
