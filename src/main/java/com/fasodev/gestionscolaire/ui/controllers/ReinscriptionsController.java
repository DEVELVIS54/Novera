package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Reinscription;
import com.fasodev.gestionscolaire.services.ReinscriptionService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Contrôleur pour la gestion des réinscriptions.
 *
 * Fonctionnalités :
 * - Onglets : En attente | Confirmées | Refusées
 * - Affichage des demandes réinscription
 * - Boutons Confirmer/Refuser pour chaque demande
 * - Statistiques
 */
public class ReinscriptionsController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private Tab tabEnAttente;
    @FXML private Tab tabConfirmees;
    @FXML private Tab tabRefusees;

    @FXML private ComboBox<String> comboAnneeEnAttente;
    @FXML private TableView<Reinscription> tableEnAttente;
    @FXML private TableColumn<Reinscription, String> colEtudiantAttente;
    @FXML private TableColumn<Reinscription, String> colClasseAttente;
    @FXML private TableColumn<Reinscription, String> colStatutAttente;
    @FXML private TableColumn<Reinscription, Void> colActionsAttente;

    @FXML private ComboBox<String> comboAnneeConfirmees;
    @FXML private TableView<Reinscription> tableConfirmees;
    @FXML private TableColumn<Reinscription, String> colEtudiantConfirmee;
    @FXML private TableColumn<Reinscription, String> colClasseConfirmee;
    @FXML private TableColumn<Reinscription, String> colConfirmeeParColumn;

    @FXML private Label lblStatistiques;

    private ObservableList<Reinscription> dataEnAttente = FXCollections.observableArrayList();
    private ObservableList<Reinscription> dataConfirmees = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Charger les années scolaires
        rafraichirComboAnnees();

        // Configurer tableau "En attente"
        colEtudiantAttente.setCellValueFactory(new PropertyValueFactory<>("nomEtudiant"));
        colClasseAttente.setCellValueFactory(new PropertyValueFactory<>("nouvelle_classe_nom"));
        colStatutAttente.setCellValueFactory(new PropertyValueFactory<>("affichageStatut"));
        colActionsAttente.setCellFactory(col -> new ActionsEnAttenteCellFactory());
        tableEnAttente.setItems(dataEnAttente);

        // Configurer tableau "Confirmées"
        colEtudiantConfirmee.setCellValueFactory(new PropertyValueFactory<>("nomEtudiant"));
        colClasseConfirmee.setCellValueFactory(new PropertyValueFactory<>("nouvelle_classe_nom"));
        colConfirmeeParColumn.setCellValueFactory(new PropertyValueFactory<>("confirmee_par"));
        tableConfirmees.setItems(dataConfirmees);

        // Listeners pour rafraîchir les onglets
        comboAnneeEnAttente.setOnAction(e -> rafraichirEnAttente());
        comboAnneeConfirmees.setOnAction(e -> rafraichirConfirmees());

        // Charger initial
        rafraichirEnAttente();
        rafraichirConfirmees();
    }

    /**
     * Recharge les années scolaires
     */
    private void rafraichirComboAnnees() {
        int yearActuelle = LocalDate.now().getYear();
        ObservableList<String> annees = FXCollections.observableArrayList();
        annees.add((yearActuelle - 1) + "-" + yearActuelle);
        annees.add(yearActuelle + "-" + (yearActuelle + 1));

        comboAnneeEnAttente.setItems(FXCollections.observableArrayList(annees));
        comboAnneeConfirmees.setItems(FXCollections.observableArrayList(annees));
        comboAnneeEnAttente.getSelectionModel().selectFirst();
        comboAnneeConfirmees.getSelectionModel().selectFirst();
    }

    /**
     * Rafraîchit la liste "En attente"
     */
    private void rafraichirEnAttente() {
        String annee = comboAnneeEnAttente.getSelectionModel().getSelectedItem();
        if (annee == null) return;

        tâcheEnArrierePlan(() -> {
            List<Reinscription> enAttente = ReinscriptionService.obtenirEnAttente(annee);
            Platform.runLater(() -> {
                dataEnAttente.clear();
                dataEnAttente.addAll(enAttente);
                mettreAJourStatistiques();
            });
        });
    }

    /**
     * Rafraîchit la liste "Confirmées"
     */
    private void rafraichirConfirmees() {
        String annee = comboAnneeConfirmees.getSelectionModel().getSelectedItem();
        if (annee == null) return;

        tâcheEnArrierePlan(() -> {
            List<Reinscription> confirmees = ReinscriptionService.obtenirConfirmees(annee);
            Platform.runLater(() -> {
                dataConfirmees.clear();
                dataConfirmees.addAll(confirmees);
            });
        });
    }

    /**
     * Met à jour les statistiques
     */
    private void mettreAJourStatistiques() {
        String annee = comboAnneeEnAttente.getSelectionModel().getSelectedItem();
        if (annee == null) return;

        List<Reinscription> enAttente = ReinscriptionService.obtenirEnAttente(annee);
        List<Reinscription> confirmees = ReinscriptionService.obtenirConfirmees(annee);

        long total = enAttente.size() + confirmees.size();
        if (total > 0) {
            double taux = (confirmees.size() * 100.0) / total;
            lblStatistiques.setText(String.format(
                "📊 Réinscriptions %s : %d confirmées, %d en attente (%.1f%% confirmées)",
                annee, confirmees.size(), enAttente.size(), taux
            ));
        }
    }

    /**
     * Classe interne pour les actions "En attente"
     */
    private class ActionsEnAttenteCellFactory extends TableCell<Reinscription, Void> {
        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }

            Reinscription reinscription = getTableRow().getItem();

            HBox actions = new HBox(5);
            actions.setAlignment(Pos.CENTER);

            Button btnConfirmer = new Button("✓ Confirmer");
            btnConfirmer.setPrefWidth(90);
            btnConfirmer.setStyle("-fx-font-size: 10; -fx-background-color: #27ae60; -fx-text-fill: white;");
            btnConfirmer.setOnAction(e -> confirmerReinscription(reinscription));

            Button btnRefuser = new Button("✗ Refuser");
            btnRefuser.setPrefWidth(90);
            btnRefuser.setStyle("-fx-font-size: 10; -fx-background-color: #e74c3c; -fx-text-fill: white;");
            btnRefuser.setOnAction(e -> refuserReinscription(reinscription));

            actions.getChildren().addAll(btnConfirmer, btnRefuser);
            setGraphic(actions);
        }

        private void confirmerReinscription(Reinscription reinscription) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Confirmation réinscription");
            dialog.setHeaderText("Parent : " + reinscription.getNomEtudiant());
            dialog.setContentText("Nouvelle classe : " + reinscription.getNouvelle_classe_nom());

            VBox content = new VBox(10);
            Label lblEmail = new Label("Confirmé par (email parent) :");
            TextField txtEmail = new TextField("parent@example.com");
            txtEmail.setPrefWidth(300);

            content.getChildren().addAll(lblEmail, txtEmail);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                tâcheEnArrierePlan(() -> {
                    try {
                        ReinscriptionService.confirmerReinscription(
                            reinscription.getId(),
                            txtEmail.getText()
                        );

                        Platform.runLater(() -> {
                            afficherSucces("✅ Réinscription confirmée !");
                            rafraichirEnAttente();
                            rafraichirConfirmees();
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> afficherErreur("Erreur : " + ex.getMessage()));
                    }
                });
            }
        }

        private void refuserReinscription(Reinscription reinscription) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Refus réinscription");
            confirmation.setHeaderText("Êtes-vous sûr ?");
            confirmation.setContentText(
                "L'étudiant " + reinscription.getNomEtudiant() +
                " sera marqué comme \"parti\""
            );

            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                tâcheEnArrierePlan(() -> {
                    try {
                        ReinscriptionService.refuserReinscription(
                            reinscription.getId(),
                            "Délai expiré ou refus parent"
                        );

                        Platform.runLater(() -> {
                            afficherSucces("✅ Réinscription refusée");
                            rafraichirEnAttente();
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> afficherErreur("Erreur : " + ex.getMessage()));
                    }
                });
            }
        }
    }

    /**
     * Affiche une alerte d'erreur
     */
    private void afficherErreur(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Affiche une alerte de succès
     */
    private void afficherSucces(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
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
