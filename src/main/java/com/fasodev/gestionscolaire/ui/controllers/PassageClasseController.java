package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.ResultatPassage;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.services.PassageClasseService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Contrôleur pour l'écran de gestion du passage en classe supérieure.
 *
 * Fonctionnalités :
 * - Sélection de classe + année scolaire
 * - Calcul automatique des statuts (ADMIS/REDOUBLE/DELIBERATION)
 * - Affichage des résultats dans une table
 * - Validation manuelle des élèves en délibération
 * - Visualisation des seuils actuels
 */
public class PassageClasseController implements Initializable {

    @FXML private ComboBox<Classe> comboClasse;
    @FXML private ComboBox<String> comboAnneeScolaire;
    @FXML private Button btnCalculer;
    @FXML private Button btnValiderDeliberations;
    @FXML private Button btnAppliquerPassages;
    @FXML private Button btnConfigSeuils;

    @FXML private Label lblSeuilAdmission;
    @FXML private Label lblSeuilRedoublement;
    @FXML private Label lblStatut;

    @FXML private TableView<ResultatPassage> tableResultats;
    @FXML private TableColumn<ResultatPassage, String> colEtudiant;
    @FXML private TableColumn<ResultatPassage, String> colClasse;
    @FXML private TableColumn<ResultatPassage, Double> colMoyenneAnnuelle;
    @FXML private TableColumn<ResultatPassage, String> colStatutCalcule;
    @FXML private TableColumn<ResultatPassage, String> colStatutFinal;
    @FXML private TableColumn<ResultatPassage, Void> colActions;

    @FXML private Label lblStatistiques;

    private ObservableList<ResultatPassage> tableData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Charger les classes
        rafraichirComboClasses();

        // Charger les années scolaires
        rafraichirComboAnneeScolaire();

        // Afficher les seuils actuels
        mettreAJourAffichageSeuils();

        // Configurer les colonnes de la table
        colEtudiant.setCellValueFactory(new PropertyValueFactory<>("nomEtudiant"));
        colClasse.setCellValueFactory(new PropertyValueFactory<>("classeOriginNom"));
        colMoyenneAnnuelle.setCellValueFactory(new PropertyValueFactory<>("moyenneAnnuelle"));
        colStatutCalcule.setCellValueFactory(new PropertyValueFactory<>("statutCalcule"));
        colStatutFinal.setCellValueFactory(new PropertyValueFactory<>("statutFinal"));

        // Colonne actions (boutons pour délibérations)
        colActions.setCellFactory(col -> new ActionsCellFactory());

        tableResultats.setItems(tableData);

        // Handlers des boutons
        btnCalculer.setOnAction(e -> calculerPourClasse());
        btnValiderDeliberations.setOnAction(e -> ouvrirDialogueDeliberations());
        btnAppliquerPassages.setOnAction(e -> appliquerPassages());
        btnConfigSeuils.setOnAction(e -> ouvrirDialogueSeuils());
    }

    /**
     * Recharge la liste des classes
     */
    private void rafraichirComboClasses() {
        List<Classe> classes = ClasseRepository.obtenirTous();
        comboClasse.setItems(FXCollections.observableArrayList(classes));

        if (!classes.isEmpty()) {
            comboClasse.getSelectionModel().selectFirst();
        }
    }

    /**
     * Recharge les années scolaires (format YYYY-YYYY)
     */
    private void rafraichirComboAnneeScolaire() {
        int yearActuelle = LocalDate.now().getYear();
        ObservableList<String> annees = FXCollections.observableArrayList();
        annees.add((yearActuelle - 1) + "-" + yearActuelle);
        annees.add(yearActuelle + "-" + (yearActuelle + 1));
        annees.add((yearActuelle + 1) + "-" + (yearActuelle + 2));

        comboAnneeScolaire.setItems(annees);
        comboAnneeScolaire.getSelectionModel().selectFirst();
    }

    /**
     * Met à jour l'affichage des seuils actuels
     */
    private void mettreAJourAffichageSeuils() {
        var config = PassageClasseService.obtenirSeuils();
        lblSeuilAdmission.setText(String.format("Seuil admission : %.2f/20", config.seuilAdmission));
        lblSeuilRedoublement.setText(String.format("Seuil redoublement : %.2f/20", config.seuilRedoublement));
    }

    /**
     * Calcule les passages pour la classe sélectionnée
     */
    @FXML
    private void calculerPourClasse() {
        Classe classe = comboClasse.getSelectionModel().getSelectedItem();
        String annee = comboAnneeScolaire.getSelectionModel().getSelectedItem();

        if (classe == null || annee == null) {
            afficherErreur("Veuillez sélectionner une classe et une année scolaire.");
            return;
        }

        tâcheEnArrierePlan(() -> {
            try {
                List<ResultatPassage> resultats = PassageClasseService.calculerPourClasse(
                    classe.getId(),
                    annee
                );

                // Enregistrer en base
                PassageClasseService.enregistrerResultats(resultats);

                Platform.runLater(() -> {
                    tableData.clear();
                    tableData.addAll(resultats);
                    mettreAJourStatistiques(resultats);
                    lblStatut.setText("✅ Calcul effectué pour " + resultats.size() + " étudiant(s)");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                    afficherErreur("Erreur lors du calcul : " + ex.getMessage())
                );
            }
        });
    }

    /**
     * Ouvre un dialogue pour valider les élèves en délibération
     */
    @FXML
    private void ouvrirDialogueDeliberations() {
        // Chercher les élèves en délibération
        List<ResultatPassage> enDeliberation = tableData.stream()
            .filter(r -> "DELIBERATION".equals(r.getStatutCalcule()) && r.getStatutFinal() == null)
            .toList();

        if (enDeliberation.isEmpty()) {
            afficherInfo("Aucun étudiant en délibération.");
            return;
        }

        // Créer un dialogue pour chaque étudiant
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Validation des Délibérations");
        dialog.setHeaderText("Choisir le statut final pour chaque étudiant en délibération");

        VBox content = new VBox(15);
        content.setPrefWidth(600);
        content.setPrefHeight(400);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        for (ResultatPassage resultat : enDeliberation) {
            HBox ligneEtudiant = creerLigneDeliberation(resultat, content);
            content.getChildren().add(ligneEtudiant);
        }

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().isPresent()) {
            lblStatut.setText("✅ Délibérations validées");
        }
    }

    /**
     * Crée une ligne pour valider une délibération
     */
    private HBox creerLigneDeliberation(ResultatPassage resultat, VBox parent) {
        HBox ligne = new HBox(15);
        ligne.setPrefHeight(60);
        ligne.setStyle("-fx-border-color: #e0e0e0; -fx-border-radius: 5; -fx-padding: 10;");
        ligne.setAlignment(Pos.CENTER_LEFT);

        // Info étudiant
        VBox infoEtudiant = new VBox(5);
        Label lblNom = new Label(resultat.getNomEtudiant());
        lblNom.setStyle("-fx-font-weight: bold;");
        Label lblMoyenne = new Label(String.format("Moy : %.2f/20", resultat.getMoyenneAnnuelle()));
        infoEtudiant.getChildren().addAll(lblNom, lblMoyenne);

        // Boutons ADMIS / REDOUBLE
        Button btnAdmis = new Button("✓ ADMIS");
        btnAdmis.setStyle("-fx-font-weight: bold; -fx-padding: 8; -fx-font-size: 12;");
        btnAdmis.setStyle(btnAdmis.getStyle() + "; -fx-text-fill: white; -fx-background-color: #27ae60;");
        btnAdmis.setOnAction(e -> {
            PassageClasseService.validerDeliberation(resultat.getId(), "ADMIS", "Directeur");
            resultat.setStatutFinal("ADMIS");
            parent.getChildren().remove(ligne);
        });

        Button btnRedouble = new Button("✗ REDOUBLE");
        btnRedouble.setStyle("-fx-font-weight: bold; -fx-padding: 8; -fx-font-size: 12;");
        btnRedouble.setStyle(btnRedouble.getStyle() + "; -fx-text-fill: white; -fx-background-color: #e74c3c;");
        btnRedouble.setOnAction(e -> {
            PassageClasseService.validerDeliberation(resultat.getId(), "REDOUBLE", "Directeur");
            resultat.setStatutFinal("REDOUBLE");
            parent.getChildren().remove(ligne);
        });

        HBox boutons = new HBox(10);
        boutons.getChildren().addAll(btnAdmis, btnRedouble);

        ligne.getChildren().addAll(infoEtudiant, new Separator(javafx.geometry.Orientation.VERTICAL), boutons);
        HBox.setHgrow(infoEtudiant, javafx.scene.layout.Priority.ALWAYS);

        return ligne;
    }

    /**
     * Applique les passages validés (change les classes des étudiants)
     */
    @FXML
    private void appliquerPassages() {
        List<ResultatPassage> aAppliquer = tableData.stream()
            .filter(r -> r.getStatutFinal() != null)
            .toList();

        if (aAppliquer.isEmpty()) {
            afficherErreur("Aucun passage à appliquer (tous doivent être validés).");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Application des passages");
        confirmation.setContentText(
            "Êtes-vous certain d'appliquer les passages pour " + aAppliquer.size() +
            " étudiant(s) ?\n\nCette action modifiera définitivement les classes des étudiants."
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            tâcheEnArrierePlan(() -> {
                try {
                    for (ResultatPassage resultat : aAppliquer) {
                        PassageClasseService.appliquerPassage(resultat);
                    }

                    Platform.runLater(() -> {
                        afficherSucces("✅ Passages appliqués avec succès !");
                        tableData.clear();
                        lblStatut.setText("Passages appliqués. Classe(s) mises à jour.");
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() ->
                        afficherErreur("Erreur lors de l'application : " + ex.getMessage())
                    );
                }
            });
        }
    }

    /**
     * Ouvre un dialogue pour modifier les seuils d'admission/redoublement
     */
    @FXML
    private void ouvrirDialogueSeuils() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Configuration des Seuils");
        dialog.setHeaderText("Modifier les seuils d'admission et redoublement");

        VBox content = new VBox(15);
        content.setPrefWidth(400);

        var config = PassageClasseService.obtenirSeuils();

        // Seuil admission
        HBox hboxAdmission = new HBox(10);
        Label lblLabelAdmission = new Label("Seuil admission (/20) :");
        lblLabelAdmission.setPrefWidth(150);
        TextField txtAdmission = new TextField(String.valueOf(config.seuilAdmission));
        txtAdmission.setPrefWidth(100);
        hboxAdmission.getChildren().addAll(lblLabelAdmission, txtAdmission);

        // Seuil redoublement
        HBox hboxRedoublement = new HBox(10);
        Label lblLabelRedoublement = new Label("Seuil redoublement (/20) :");
        lblLabelRedoublement.setPrefWidth(150);
        TextField txtRedoublement = new TextField(String.valueOf(config.seuilRedoublement));
        txtRedoublement.setPrefWidth(100);
        hboxRedoublement.getChildren().addAll(lblLabelRedoublement, txtRedoublement);

        Label lblInfo = new Label(
            "Logique :\n" +
            "• Moy >= Seuil admission → Admis (passe automatique)\n" +
            "• Moy < Seuil redoublement → Redouble\n" +
            "• Entre les deux → Délibération (conseil de classe)"
        );
        lblInfo.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        lblInfo.setWrapText(true);

        content.getChildren().addAll(hboxAdmission, hboxRedoublement, lblInfo);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                double admission = Double.parseDouble(txtAdmission.getText());
                double redoublement = Double.parseDouble(txtRedoublement.getText());

                if (redoublement >= admission) {
                    afficherErreur("Le seuil redoublement doit être < seuil admission");
                    return;
                }

                PassageClasseService.mettreAJourSeuils(admission, redoublement);
                mettreAJourAffichageSeuils();
                afficherSucces("Seuils mis à jour !");

            } catch (NumberFormatException ex) {
                afficherErreur("Veuillez entrer des nombres valides.");
            }
        }
    }

    /**
     * Met à jour l'affichage des statistiques
     */
    private void mettreAJourStatistiques(List<ResultatPassage> resultats) {
        long admis = resultats.stream().filter(r -> "ADMIS".equals(r.getStatutCalcule())).count();
        long redouble = resultats.stream().filter(r -> "REDOUBLE".equals(r.getStatutCalcule())).count();
        long deliberation = resultats.stream().filter(r -> "DELIBERATION".equals(r.getStatutCalcule())).count();

        lblStatistiques.setText(String.format(
            "📊 Résultats : %d ADMIS | %d REDOUBLE | %d DÉLIBÉRATION",
            admis, redouble, deliberation
        ));
    }

    /**
     * Classe interne pour ajouter des boutons action à la table
     */
    private class ActionsCellFactory extends TableCell<ResultatPassage, Void> {
        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }

            ResultatPassage resultat = getTableRow().getItem();

            HBox actions = new HBox(5);
            actions.setAlignment(Pos.CENTER);

            // Bouton couleur selon statut calculé
            if ("DELIBERATION".equals(resultat.getStatutCalcule()) && resultat.getStatutFinal() == null) {
                Button btnValider = new Button("Valider");
                btnValider.setPrefWidth(70);
                btnValider.setStyle("-fx-font-size: 10;");
                btnValider.setOnAction(e -> {
                    // Ouvrir un mini-dialogue pour ce seul étudiant
                    Dialog<String> choixDialog = new Dialog<>();
                    choixDialog.setTitle("Choix pour " + resultat.getNomEtudiant());
                    choixDialog.setHeaderText("Moyenne : " + String.format("%.2f/20", resultat.getMoyenneAnnuelle()));

                    VBox choixContent = new VBox(10);
                    Button btnA = new Button("✓ Admettre");
                    btnA.setPrefWidth(150);
                    btnA.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                    btnA.setOnAction(ae -> choixDialog.setResult("ADMIS"));

                    Button btnR = new Button("✗ Redoubler");
                    btnR.setPrefWidth(150);
                    btnR.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                    btnR.setOnAction(ae -> choixDialog.setResult("REDOUBLE"));

                    choixContent.getChildren().addAll(btnA, btnR);
                    choixContent.setAlignment(Pos.CENTER);

                    choixDialog.getDialogPane().setContent(choixContent);

                    if (choixDialog.showAndWait().isPresent()) {
                        String statut = choixDialog.getResult();
                        PassageClasseService.validerDeliberation(resultat.getId(), statut, "Directeur");
                        resultat.setStatutFinal(statut);
                        tableResultats.refresh();
                    }
                });

                actions.getChildren().add(btnValider);
            } else if (resultat.getStatutFinal() != null) {
                // Afficher un badge du statut final
                Label lblFinal = new Label("✓ " + resultat.getStatutFinal());
                lblFinal.setStyle(
                    "ADMIS".equals(resultat.getStatutFinal()) ?
                    "-fx-text-fill: green; -fx-font-weight: bold;" :
                    "-fx-text-fill: red; -fx-font-weight: bold;"
                );
                actions.getChildren().add(lblFinal);
            }

            setGraphic(actions);
        }
    }

    /**
     * Affiche une alerte d'erreur
     */
    private void afficherErreur(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte de succès
     */
    private void afficherSucces(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte d'information
     */
    private void afficherInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Exécute une tâche en arrière-plan
     */
    private void tâcheEnArrierePlan(Runnable task) {
        new Thread(task).start();
    }
}
