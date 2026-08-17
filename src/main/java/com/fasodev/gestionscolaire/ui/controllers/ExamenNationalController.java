package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.ResultatExamenNational;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.services.ExamenNationalService;
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
 * Contrôleur pour la saisie des résultats d'examen national.
 *
 * Fonctionnalités :
 * - Sélection classe examen + année
 * - Création dossiers examen (1 par étudiant)
 * - Saisie résultats : ADMIS ou REFUSÉ (+ Redouble/Parti)
 * - Table des résultats avec statuts
 * - Bouton "Créer réinscriptions" pour les admis
 * - Bouton "Appliquer décisions" pour les refusés
 */
public class ExamenNationalController implements Initializable {

    @FXML private ComboBox<Classe> comboClasseExamen;
    @FXML private ComboBox<String> comboAnnee;
    @FXML private Button btnCreerDossiers;
    @FXML private Button btnCreerReinscriptions;
    @FXML private Button btnAppliquerDecisions;

    @FXML private Label lblStatut;
    @FXML private Label lblNomExamen;

    @FXML private TableView<ResultatExamenNational> tableResultats;
    @FXML private TableColumn<ResultatExamenNational, String> colEtudiant;
    @FXML private TableColumn<ResultatExamenNational, String> colExamen;
    @FXML private TableColumn<ResultatExamenNational, String> colResultat;
    @FXML private TableColumn<ResultatExamenNational, String> colDecision;
    @FXML private TableColumn<ResultatExamenNational, String> colStatutFinal;
    @FXML private TableColumn<ResultatExamenNational, Void> colActions;

    @FXML private Label lblStatistiques;

    private ObservableList<ResultatExamenNational> tableData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Charger les classes d'examen
        rafraichirComboClassesExamen();

        // Charger les années scolaires
        rafraichirComboAnnees();

        // Configurer les colonnes de la table
        colEtudiant.setCellValueFactory(new PropertyValueFactory<>("nomEtudiant"));
        colExamen.setCellValueFactory(new PropertyValueFactory<>("nomExamen"));
        colResultat.setCellValueFactory(new PropertyValueFactory<>("affichageResultat"));
        colDecision.setCellValueFactory(new PropertyValueFactory<>("decision_si_refuse"));
        colStatutFinal.setCellValueFactory(new PropertyValueFactory<>("statut_final"));

        // Colonne actions (boutons saisie)
        colActions.setCellFactory(col -> new SaisieResultatCellFactory());

        tableResultats.setItems(tableData);

        // Handlers des boutons
        btnCreerDossiers.setOnAction(e -> creerDossiersExamen());
        btnCreerReinscriptions.setOnAction(e -> creerReinscriptions());
        btnAppliquerDecisions.setOnAction(e -> appliquerDecisions());
    }

    /**
     * Recharge les classes d'examen
     */
    private void rafraichirComboClassesExamen() {
        List<Classe> classesExamen = ClasseRepository.obtenirTous().stream()
            .filter(Classe::isEstClasseExamen)
            .toList();

        comboClasseExamen.setItems(FXCollections.observableArrayList(classesExamen));

        if (!classesExamen.isEmpty()) {
            comboClasseExamen.getSelectionModel().selectFirst();
            mettreAJourNomExamen();
        }

        comboClasseExamen.setOnAction(e -> mettreAJourNomExamen());
    }

    /**
     * Recharge les années scolaires
     */
    private void rafraichirComboAnnees() {
        int yearActuelle = LocalDate.now().getYear();
        ObservableList<String> annees = FXCollections.observableArrayList();
        annees.add((yearActuelle - 1) + "-" + yearActuelle);
        annees.add(yearActuelle + "-" + (yearActuelle + 1));

        comboAnnee.setItems(annees);
        comboAnnee.getSelectionModel().selectFirst();
    }

    /**
     * Met à jour l'affichage du nom de l'examen
     */
    private void mettreAJourNomExamen() {
        Classe classe = comboClasseExamen.getSelectionModel().getSelectedItem();
        if (classe != null) {
            lblNomExamen.setText("📋 Examen : " + classe.getNomExamen());
        }
    }

    /**
     * Crée les dossiers d'examen pour tous les étudiants de la classe
     */
    @FXML
    private void creerDossiersExamen() {
        Classe classe = comboClasseExamen.getSelectionModel().getSelectedItem();
        String annee = comboAnnee.getSelectionModel().getSelectedItem();

        if (classe == null || annee == null) {
            afficherErreur("Veuillez sélectionner une classe et une année.");
            return;
        }

        tâcheEnArrierePlan(() -> {
            try {
                List<ResultatExamenNational> dossiers = ExamenNationalService.creerDossiersPour(
                    classe.getId(), annee
                );

                Platform.runLater(() -> {
                    tableData.clear();
                    tableData.addAll(dossiers);
                    lblStatut.setText("✅ " + dossiers.size() + " dossier(s) créé(s)");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                    afficherErreur("Erreur création dossiers : " + ex.getMessage())
                );
            }
        });
    }

    /**
     * Crée les demandes de réinscription pour les admis
     */
    @FXML
    private void creerReinscriptions() {
        List<ResultatExamenNational> admis = tableData.stream()
            .filter(r -> "admis".equals(r.getResultat()))
            .toList();

        if (admis.isEmpty()) {
            afficherInfo("Aucun étudiant admis pour créer des réinscriptions.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Création des réinscriptions");
        confirmation.setContentText(
            "Créer les demandes de réinscription pour " + admis.size() +
            " étudiant(s) admis ?\n\n" +
            "(Terminale → Sortie automatique, autres → En attente réinscription)"
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            tâcheEnArrierePlan(() -> {
                try {
                    ExamenNationalService.creerReinscriptionsApres(admis);

                    Platform.runLater(() -> {
                        afficherSucces("✅ Demandes de réinscription créées !");
                        tableResultats.refresh();
                        mettreAJourStatistiques();
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() ->
                        afficherErreur("Erreur création réinscriptions : " + ex.getMessage())
                    );
                }
            });
        }
    }

    /**
     * Applique les décisions pour les refusés
     */
    @FXML
    private void appliquerDecisions() {
        List<ResultatExamenNational> refuses = tableData.stream()
            .filter(r -> "refuse".equals(r.getResultat()))
            .toList();

        if (refuses.isEmpty()) {
            afficherInfo("Aucun étudiant refusé avec décision à appliquer.");
            return;
        }

        // Vérifier que toutes les décisions sont prises
        List<ResultatExamenNational> sanDecision = refuses.stream()
            .filter(r -> r.getDecision_si_refuse() == null)
            .toList();

        if (!sanDecision.isEmpty()) {
            afficherErreur("Veuillez d'abord saisir une décision (Redouble/Parti) pour " +
                sanDecision.size() + " étudiant(s).");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Application des décisions");
        confirmation.setContentText(
            "Appliquer les décisions pour " + refuses.size() + " étudiant(s) refusé(s) ?\n\n" +
            "Cette action modifiera définitivement les statuts des étudiants."
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            tâcheEnArrierePlan(() -> {
                try {
                    ExamenNationalService.appliquerDecisionsRefuses(refuses);

                    Platform.runLater(() -> {
                        afficherSucces("✅ Décisions appliquées !");
                        tableResultats.refresh();
                        mettreAJourStatistiques();
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() ->
                        afficherErreur("Erreur application décisions : " + ex.getMessage())
                    );
                }
            });
        }
    }

    /**
     * Met à jour les statistiques
     */
    private void mettreAJourStatistiques() {
        long admis = tableData.stream().filter(r -> "admis".equals(r.getResultat())).count();
        long refuses = tableData.stream().filter(r -> "refuse".equals(r.getResultat())).count();

        lblStatistiques.setText(String.format(
            "📊 Résultats : %d ADMIS | %d REFUSÉS",
            admis, refuses
        ));
    }

    /**
     * Classe interne pour les actions de saisie résultat
     */
    private class SaisieResultatCellFactory extends TableCell<ResultatExamenNational, Void> {
        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }

            ResultatExamenNational resultat = getTableRow().getItem();

            HBox actions = new HBox(5);
            actions.setAlignment(Pos.CENTER);

            if (resultat.getResultat() == null) {
                // Boutons pour choisir ADMIS ou REFUSÉ
                Button btnAdmis = new Button("✓ Admis");
                btnAdmis.setPrefWidth(70);
                btnAdmis.setStyle("-fx-font-size: 10; -fx-background-color: #27ae60; -fx-text-fill: white;");
                btnAdmis.setOnAction(e -> saisirResultat(resultat, "admis", null));

                Button btnRefuse = new Button("✗ Refusé");
                btnRefuse.setPrefWidth(70);
                btnRefuse.setStyle("-fx-font-size: 10; -fx-background-color: #e74c3c; -fx-text-fill: white;");
                btnRefuse.setOnAction(e -> ouvrirDialogueRefuse(resultat));

                actions.getChildren().addAll(btnAdmis, btnRefuse);

            } else if (resultat.getResultat().equals("refuse") && resultat.getDecision_si_refuse() == null) {
                // Boutons pour choisir Redouble ou Parti
                Button btnRedouble = new Button("Redouble");
                btnRedouble.setPrefWidth(80);
                btnRedouble.setStyle("-fx-font-size: 10;");
                btnRedouble.setOnAction(e -> saisirResultat(resultat, "refuse", "redouble"));

                Button btnParti = new Button("Parti");
                btnParti.setPrefWidth(80);
                btnParti.setStyle("-fx-font-size: 10;");
                btnParti.setOnAction(e -> saisirResultat(resultat, "refuse", "parti"));

                actions.getChildren().addAll(btnRedouble, btnParti);

            } else if (resultat.getResultat() != null) {
                // Afficher badge du résultat
                Label lblResultat = new Label(resultat.getAffichageResultat());
                lblResultat.setStyle(
                    "admis".equals(resultat.getResultat()) ?
                    "-fx-text-fill: green; -fx-font-weight: bold;" :
                    "-fx-text-fill: red; -fx-font-weight: bold;"
                );
                actions.getChildren().add(lblResultat);
            }

            setGraphic(actions);
        }

        private void ouvrirDialogueRefuse(ResultatExamenNational resultat) {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Décision après refus");
            dialog.setHeaderText("Que devient l'étudiant ?");

            VBox content = new VBox(15);
            content.setPrefWidth(300);

            Button btnRedouble = new Button("📝 Redouble sa classe");
            btnRedouble.setPrefWidth(250);
            btnRedouble.setPrefHeight(40);
            btnRedouble.setStyle("-fx-font-size: 12;");
            btnRedouble.setOnAction(e -> {
                saisirResultat(resultat, "refuse", "redouble");
                dialog.close();
            });

            Button btnParti = new Button("🚪 Quitte l'établissement");
            btnParti.setPrefWidth(250);
            btnParti.setPrefHeight(40);
            btnParti.setStyle("-fx-font-size: 12;");
            btnParti.setOnAction(e -> {
                saisirResultat(resultat, "refuse", "parti");
                dialog.close();
            });

            content.getChildren().addAll(btnRedouble, btnParti);
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        }

        private void saisirResultat(ResultatExamenNational resultat, String resultatVal, String decision) {
            try {
                ExamenNationalService.enregistrerResultat(
                    resultat.getId(),
                    resultatVal,
                    decision != null ? decision : "",
                    "Secrétaire"
                );

                resultat.setResultat(resultatVal);
                if (decision != null) {
                    resultat.setDecision_si_refuse(decision);
                }

                tableResultats.refresh();
                mettreAJourStatistiques();
            } catch (Exception ex) {
                afficherErreur("Erreur saisie : " + ex.getMessage());
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
     * Affiche une alerte d'information
     */
    private void afficherInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
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
