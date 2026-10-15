package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.services.CalculMoyenneTrimestreService;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MoyennesController implements Initializable {

    @FXML private ComboBox<Classe> classeCombo;
    @FXML private ComboBox<Integer> trimestreCombo;
    @FXML private Label statutLabel;
    @FXML private Button calculerButton;
    @FXML private Button deverrouillerButton;
    @FXML private VBox deverrouillageBox;
    @FXML private TextArea raisonField;
    @FXML private Label messageLabel;
    @FXML private Label erreurLabel;

    @FXML private TableView<MoyenneResultat> resultatsTable;
    @FXML private TableColumn<MoyenneResultat, String> colRang;
    @FXML private TableColumn<MoyenneResultat, String> colNom;
    @FXML private TableColumn<MoyenneResultat, String> colMoyenne;

    private final CalculMoyenneTrimestreService calculService = new CalculMoyenneTrimestreService();
    private final ClasseService classeService = new ClasseService();
    private final ObservableList<MoyenneResultat> resultatsData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colRang.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(String.valueOf(cell.getValue().getRang())));
        colNom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getEtudiantNomComplet()));
        colMoyenne.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", cell.getValue().getMoyenne())));

        resultatsTable.setItems(resultatsData);

        classeCombo.setItems(FXCollections.observableArrayList(classeService.listerToutes()));
        trimestreCombo.setItems(FXCollections.observableArrayList(1, 2, 3));

        classeCombo.setOnAction(e -> rafraichirEtat());
        trimestreCombo.setOnAction(e -> rafraichirEtat());
    }

    private void rafraichirEtat() {

        masquerMessages();
        resultatsData.clear();
        deverrouillageBox.setVisible(false);
        deverrouillageBox.setManaged(false);

        Classe classe = classeCombo.getValue();
        Integer trimestre = trimestreCombo.getValue();

        if (classe == null || trimestre == null) {
            statutLabel.setText("");
            calculerButton.setDisable(true);
            deverrouillerButton.setVisible(false);
            deverrouillerButton.setManaged(false);
            return;
        }

        String statut = calculService.getStatut(classe.getId(), trimestre);
        boolean verrouille = "calcule_verrouille".equals(statut);

        if (verrouille) {
            statutLabel.setText("🔒 Verrouillé");
            calculerButton.setDisable(true);
            deverrouillerButton.setVisible(true);
            deverrouillerButton.setManaged(true);

            List<MoyenneResultat> resultats = calculService.getMoyennesCalculees(classe.getId(), trimestre);
            resultatsData.setAll(resultats);

        } else {
            statutLabel.setText("🔓 Non calculé");
            calculerButton.setDisable(false);
            deverrouillerButton.setVisible(false);
            deverrouillerButton.setManaged(false);
        }
    }

    @FXML
    public void onCalculerClick() {

        Classe classe = classeCombo.getValue();
        Integer trimestre = trimestreCombo.getValue();

        if (classe == null || trimestre == null) {
            afficherErreur("Veuillez sélectionner une classe et un trimestre.");
            return;
        }

        String utilisateur = SessionManager.getUtilisateurConnecte() != null
            ? SessionManager.getUtilisateurConnecte().getNomUtilisateur() : "inconnu";

        try {
            List<MoyenneResultat> resultats =
                calculService.calculerMoyennesClasse(classe.getId(), trimestre, utilisateur);

            resultatsData.setAll(resultats);
            afficherMessage("✅ Moyennes calculées et verrouillées pour " +
                resultats.size() + " étudiant(s).");
            rafraichirEtat();

        } catch (IllegalStateException e) {
            afficherErreur(e.getMessage());
        }
    }

    @FXML
    public void onDeverrouillerClick() {
        deverrouillageBox.setVisible(true);
        deverrouillageBox.setManaged(true);
    }

    @FXML
    public void onConfirmerDeverrouillageClick() {

        Classe classe = classeCombo.getValue();
        Integer trimestre = trimestreCombo.getValue();
        String raison = raisonField.getText();

        try {
            calculService.deverrouiller(classe.getId(), trimestre, raison);
            afficherMessage("✅ Déverrouillé. Vous pouvez maintenant modifier les notes.");
            raisonField.clear();
            rafraichirEtat();

        } catch (IllegalArgumentException e) {
            afficherErreur(e.getMessage());
        }
    }

    private void afficherErreur(String message) {
        erreurLabel.setText(message);
        erreurLabel.setVisible(true);
        erreurLabel.setManaged(true);
        messageLabel.setText("");
    }

    private void afficherMessage(String message) {
        messageLabel.setText(message);
        erreurLabel.setVisible(false);
        erreurLabel.setManaged(false);
    }

    private void masquerMessages() {
        erreurLabel.setVisible(false);
        erreurLabel.setManaged(false);
        messageLabel.setText("");
    }
}
