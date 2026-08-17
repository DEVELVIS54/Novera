package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Matiere;
import com.fasodev.gestionscolaire.models.NotesMatiere;
import com.fasodev.gestionscolaire.services.CalculMoyenneTrimestreService;
import com.fasodev.gestionscolaire.services.ClasseService;
import com.fasodev.gestionscolaire.services.EtudiantService;
import com.fasodev.gestionscolaire.services.MatiereService;
import com.fasodev.gestionscolaire.services.NoteService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class NotesController implements Initializable {

    @FXML private ComboBox<Classe> classeCombo;
    @FXML private ComboBox<Integer> trimestreCombo;
    @FXML private Label verrouBadge;

    @FXML private TableView<Etudiant> etudiantsTable;
    @FXML private TableColumn<Etudiant, String> colNom;
    @FXML private TableColumn<Etudiant, String> colPrenom;

    @FXML private Label etudiantSelectionneLabel;
    @FXML private Label messageLabel;
    @FXML private VBox matieresContainer;

    private final ClasseService classeService = new ClasseService();
    private final EtudiantService etudiantService = new EtudiantService();
    private final MatiereService matiereService = new MatiereService();
    private final NoteService noteService = new NoteService();
    private final CalculMoyenneTrimestreService calculService = new CalculMoyenneTrimestreService();

    private final ObservableList<Etudiant> etudiantsData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        colNom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getNom()));
        colPrenom.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(cell.getValue().getPrenom()));

        etudiantsTable.setItems(etudiantsData);

        classeCombo.setItems(FXCollections.observableArrayList(classeService.listerToutes()));
        trimestreCombo.setItems(FXCollections.observableArrayList(1, 2, 3));
        trimestreCombo.setValue(1);

        classeCombo.setOnAction(e -> onClasseOuTrimestreChange());
        trimestreCombo.setOnAction(e -> onClasseOuTrimestreChange());

        etudiantsTable.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau != null) {
                afficherNotesDeLEtudiant(nouveau);
            }
        });

        afficherEtatVide("Sélectionnez une classe pour voir la liste des étudiants.");
    }

    /**
     * Appelé quand la classe ou le trimestre change : recharge la liste
     * des étudiants, met à jour le statut de verrouillage, et vide
     * la zone de saisie (aucun étudiant n'est plus sélectionné).
     */
    private void onClasseOuTrimestreChange() {

        Classe classe = classeCombo.getValue();
        Integer trimestre = trimestreCombo.getValue();

        etudiantsTable.getSelectionModel().clearSelection();
        etudiantsData.clear();

        if (classe == null || trimestre == null) {
            afficherEtatVide("Sélectionnez une classe pour voir la liste des étudiants.");
            mettreAJourBadgeVerrou(false);
            return;
        }

        etudiantsData.setAll(etudiantService.listerParClasse(classe.getId()));

        String statut = calculService.getStatut(classe.getId(), trimestre);
        mettreAJourBadgeVerrou("calcule_verrouille".equals(statut));

        afficherEtatVide("👈 Sélectionnez un étudiant dans la liste");
    }

    private void mettreAJourBadgeVerrou(boolean verrouille) {
        verrouBadge.setVisible(verrouille);
        verrouBadge.setManaged(verrouille);
        if (verrouille) {
            verrouBadge.setText(
                "🔒 Moyennes verrouillées pour ce trimestre. " +
                "Allez dans \"Calcul moyennes\" pour déverrouiller si besoin."
            );
        }
    }

    private void afficherEtatVide(String message) {
        etudiantSelectionneLabel.setText(message);
        matieresContainer.getChildren().clear();
        masquerMessage();
    }

    private void afficherNotesDeLEtudiant(Etudiant etudiant) {

        matieresContainer.getChildren().clear();
        masquerMessage();

        Classe classe = classeCombo.getValue();
        Integer trimestre = trimestreCombo.getValue();

        if (classe == null || trimestre == null) {
            return;
        }

        etudiantSelectionneLabel.setText("📝 " + etudiant.getNomComplet());

        String statut = calculService.getStatut(classe.getId(), trimestre);
        boolean verrouille = "calcule_verrouille".equals(statut);

        List<Matiere> matieres = matiereService.listerParClasse(classe.getId());
        List<NotesMatiere> notesActuelles = noteService.recupererNotesParMatiere(
            etudiant.getId(), classe.getId(), trimestre
        );

        if (matieres.isEmpty()) {
            matieresContainer.getChildren().add(
                new Label("Aucune matière configurée pour cette classe. " +
                    "Rendez-vous dans \"Matières\" pour en créer.")
            );
            return;
        }

        for (Matiere matiere : matieres) {
            NotesMatiere nm = notesActuelles.stream()
                .filter(n -> n.getMatiereId() == matiere.getId())
                .findFirst()
                .orElse(new NotesMatiere(matiere.getId(), matiere.getNom(), matiere.getCoefficient()));

            matieresContainer.getChildren().add(
                construireLigneMatiere(matiere, nm, etudiant.getId(), classe.getId(), trimestre, verrouille)
            );
        }
    }

    /**
     * Construit dynamiquement le bloc de saisie d'UNE matière :
     * un toggle Détaillé/Direct, et les champs correspondants.
     */
    private VBox construireLigneMatiere(Matiere matiere, NotesMatiere notesExistantes,
                                          int etudiantId, int classeId, int trimestre, boolean verrouille) {

        VBox card = new VBox(8);
        card.getStyleClass().add("matiere-card");
        card.setPadding(new Insets(14));

        Label titre = new Label(
            matiere.getNom() + "  (coeff. " + matiere.getCoefficient() +
            (matiere.getNomProfesseur() != null && !matiere.getNomProfesseur().isBlank()
                ? " — " + matiere.getNomProfesseur() : "") + ")"
        );
        titre.getStyleClass().add("matiere-title");

        ToggleGroup toggleGroup = new ToggleGroup();
        RadioButton detailleRadio = new RadioButton("Saisie détaillée (2 devoirs + composition)");
        RadioButton directRadio = new RadioButton("Saisie directe (moyenne matière)");
        detailleRadio.setToggleGroup(toggleGroup);
        directRadio.setToggleGroup(toggleGroup);
        detailleRadio.setDisable(verrouille);
        directRadio.setDisable(verrouille);

        boolean modeDirect = notesExistantes.estSaisieDirecte();
        directRadio.setSelected(modeDirect);
        detailleRadio.setSelected(!modeDirect);

        HBox toggleBox = new HBox(15, detailleRadio, directRadio);

        // --- Zone détaillée ---
        HBox detailleBox = new HBox(10);
        TextField devoir1Field = new TextField(
            notesExistantes.getDevoir1() != null ? String.valueOf(notesExistantes.getDevoir1()) : "");
        TextField devoir2Field = new TextField(
            notesExistantes.getDevoir2() != null ? String.valueOf(notesExistantes.getDevoir2()) : "");
        TextField compositionField = new TextField(
            notesExistantes.getComposition() != null ? String.valueOf(notesExistantes.getComposition()) : "");
        devoir1Field.setPromptText("Devoir 1");
        devoir2Field.setPromptText("Devoir 2");
        compositionField.setPromptText("Composition");
        devoir1Field.setPrefWidth(90);
        devoir2Field.setPrefWidth(90);
        compositionField.setPrefWidth(90);
        devoir1Field.setDisable(verrouille);
        devoir2Field.setDisable(verrouille);
        compositionField.setDisable(verrouille);

        Button enregistrerDetailleBtn = new Button("Enregistrer");
        enregistrerDetailleBtn.getStyleClass().add("primary-button-small");
        enregistrerDetailleBtn.setDisable(verrouille);
        enregistrerDetailleBtn.setOnAction(e -> enregistrerDetaille(
            etudiantId, matiere.getId(), classeId, trimestre,
            devoir1Field.getText(), devoir2Field.getText(), compositionField.getText()
        ));

        detailleBox.getChildren().addAll(
            new Label("D1:"), devoir1Field, new Label("D2:"), devoir2Field,
            new Label("Comp:"), compositionField, enregistrerDetailleBtn
        );

        // --- Zone directe ---
        HBox directBox = new HBox(10);
        TextField moyenneDirecteField = new TextField(
            notesExistantes.getMoyenneDirecte() != null ? String.valueOf(notesExistantes.getMoyenneDirecte()) : "");
        moyenneDirecteField.setPromptText("Moyenne matière");
        moyenneDirecteField.setPrefWidth(100);
        moyenneDirecteField.setDisable(verrouille);

        Button enregistrerDirectBtn = new Button("Enregistrer");
        enregistrerDirectBtn.getStyleClass().add("primary-button-small");
        enregistrerDirectBtn.setDisable(verrouille);
        enregistrerDirectBtn.setOnAction(e -> enregistrerDirect(
            etudiantId, matiere.getId(), classeId, trimestre, moyenneDirecteField.getText()
        ));

        directBox.getChildren().addAll(new Label("Moyenne:"), moyenneDirecteField, enregistrerDirectBtn);

        detailleBox.setVisible(!modeDirect);
        detailleBox.setManaged(!modeDirect);
        directBox.setVisible(modeDirect);
        directBox.setManaged(modeDirect);

        detailleRadio.setOnAction(e -> {
            detailleBox.setVisible(true);
            detailleBox.setManaged(true);
            directBox.setVisible(false);
            directBox.setManaged(false);
        });
        directRadio.setOnAction(e -> {
            detailleBox.setVisible(false);
            detailleBox.setManaged(false);
            directBox.setVisible(true);
            directBox.setManaged(true);
        });

        Label moyenneLabel = new Label(
            "Moyenne matière actuelle : " + String.format("%.2f", notesExistantes.calculerMoyenneMatiere())
        );
        moyenneLabel.getStyleClass().add("moyenne-preview");

        card.getChildren().addAll(titre, toggleBox, detailleBox, directBox, moyenneLabel);
        return card;
    }

    private void enregistrerDetaille(int etudiantId, int matiereId, int classeId, int trimestre,
                                       String d1, String d2, String comp) {
        try {
            if (!d1.isBlank()) {
                noteService.saisirNote(etudiantId, matiereId, classeId,
                    com.fasodev.gestionscolaire.models.Note.TYPE_DEVOIR1, trimestre, parseValeur(d1));
            }
            if (!d2.isBlank()) {
                noteService.saisirNote(etudiantId, matiereId, classeId,
                    com.fasodev.gestionscolaire.models.Note.TYPE_DEVOIR2, trimestre, parseValeur(d2));
            }
            if (!comp.isBlank()) {
                noteService.saisirNote(etudiantId, matiereId, classeId,
                    com.fasodev.gestionscolaire.models.Note.TYPE_COMPOSITION, trimestre, parseValeur(comp));
            }
            noteService.effacerMoyenneDirecte(etudiantId, matiereId, classeId, trimestre);

            afficherMessage("✅ Notes enregistrées.");
            rafraichirEtudiantCourant();

        } catch (IllegalArgumentException | IllegalStateException ex) {
            afficherMessage("❌ " + ex.getMessage());
        }
    }

    private void enregistrerDirect(int etudiantId, int matiereId, int classeId, int trimestre, String valeur) {
        try {
            if (valeur.isBlank()) {
                afficherMessage("❌ Veuillez saisir une moyenne.");
                return;
            }
            noteService.saisirNote(etudiantId, matiereId, classeId,
                com.fasodev.gestionscolaire.models.Note.TYPE_MOYENNE_DIRECTE, trimestre, parseValeur(valeur));

            afficherMessage("✅ Moyenne directe enregistrée.");
            rafraichirEtudiantCourant();

        } catch (IllegalArgumentException | IllegalStateException ex) {
            afficherMessage("❌ " + ex.getMessage());
        }
    }

    /**
     * Recharge les cartes de matières pour l'étudiant actuellement
     * sélectionné (après un enregistrement, pour montrer les valeurs à jour).
     */
    private void rafraichirEtudiantCourant() {
        Etudiant selectionne = etudiantsTable.getSelectionModel().getSelectedItem();
        if (selectionne != null) {
            afficherNotesDeLEtudiant(selectionne);
        }
    }

    private double parseValeur(String texte) {
        try {
            return Double.parseDouble(texte.replace(",", "."));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + texte + "\" n'est pas un nombre valide.");
        }
    }

    private void afficherMessage(String message) {
        messageLabel.setText(message);
    }

    private void masquerMessage() {
        messageLabel.setText("");
    }
}
