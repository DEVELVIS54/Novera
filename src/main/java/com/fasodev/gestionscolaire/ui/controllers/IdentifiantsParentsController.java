package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.ConfigurationEcole;
import com.fasodev.gestionscolaire.models.ParentIdentifiantGenere;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.services.FicheIdentifiantPdfService;
import com.fasodev.gestionscolaire.services.ParentIdentifiantService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class IdentifiantsParentsController implements Initializable {

    @FXML private ComboBox<Classe> comboClasse;
    @FXML private Button btnGenererPourClasse;
    @FXML private Button btnGenererTous;
    @FXML private Button btnImprimerPdf;
    @FXML private Button btnVoirActifs;

    @FXML private TableView<ParentIdentifiantGenere> tableIdentifiants;
    @FXML private TableColumn<ParentIdentifiantGenere, String> colEtudiant;
    @FXML private TableColumn<ParentIdentifiantGenere, String> colClasse;
    @FXML private TableColumn<ParentIdentifiantGenere, String> colIdentifiant;
    @FXML private TableColumn<ParentIdentifiantGenere, String> colMotDePasse;

    @FXML private Label lblStatut;
    @FXML private CheckBox chkForceRegenerer;

    private ObservableList<ParentIdentifiantGenere> tableData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Charger les classes
        rafraichirComboClasses();

        // Configurer les colonnes de la table
        colEtudiant.setCellValueFactory(new PropertyValueFactory<>("nomEtudiant"));
        colClasse.setCellValueFactory(new PropertyValueFactory<>("classeNom"));
        colIdentifiant.setCellValueFactory(new PropertyValueFactory<>("identifiant"));
        colMotDePasse.setCellValueFactory(new PropertyValueFactory<>("motDePasseClair"));

        tableIdentifiants.setItems(tableData);

        // Handlers des boutons
        btnGenererPourClasse.setOnAction(e -> genererPourClasseSelectionnee());
        btnGenererTous.setOnAction(e -> genererPourToutesClasses());
        btnImprimerPdf.setOnAction(e -> imprimerEnPdf());
        btnVoirActifs.setOnAction(e -> afficherAccesActifs());
    }

    /**
     * Recharge la liste des classes dans le combo
     */
    private void rafraichirComboClasses() {
        List<Classe> classes = ClasseRepository.obtenirTous();
        comboClasse.setItems(FXCollections.observableArrayList(classes));

        if (!classes.isEmpty()) {
            comboClasse.getSelectionModel().selectFirst();
        }
    }

    /**
     * Génère les identifiants pour la classe sélectionnée
     */
    @FXML
    private void genererPourClasseSelectionnee() {
        Classe classe = comboClasse.getSelectionModel().getSelectedItem();

        if (classe == null) {
            afficherErreur("Veuillez sélectionner une classe.");
            return;
        }

        boolean forceRegenerer = chkForceRegenerer.isSelected();

        tâcheEnArrierePlan(() -> {
            try {
                List<ParentIdentifiantGenere> resultats = ParentIdentifiantService.genererPourClasse(
                    classe.getId(),
                    obtenirSigleEcole(),
                    forceRegenerer
                );

                Platform.runLater(() -> {
                    tableData.clear();
                    tableData.addAll(resultats);
                    lblStatut.setText("✅ " + resultats.size() + " identifiant(s) généré(s) pour " + classe.getNom());
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                    afficherErreur("Erreur lors de la génération : " + ex.getMessage())
                );
            }
        });
    }

    /**
     * Génère les identifiants pour TOUTES les classes
     */
    @FXML
    private void genererPourToutesClasses() {
        boolean forceRegenerer = chkForceRegenerer.isSelected();

        // Confirmation
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Génération en masse");
        confirmation.setContentText(
            (forceRegenerer ? "Regénérer" : "Générer") + " les identifiants pour TOUTES les classes ?\n" +
            "(Les étudiants inactifs seront ignorés)"
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL) {
            return;
        }

        tâcheEnArrierePlan(() -> {
            try {
                List<ParentIdentifiantGenere> resultats = ParentIdentifiantService.genererPourClasse(
                    0, // 0 = toutes les classes
                    obtenirSigleEcole(),
                    forceRegenerer
                );

                Platform.runLater(() -> {
                    tableData.clear();
                    tableData.addAll(resultats);
                    lblStatut.setText("✅ " + resultats.size() + " identifiant(s) généré(s) pour toutes les classes");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                    afficherErreur("Erreur lors de la génération : " + ex.getMessage())
                );
            }
        });
    }

    /**
     * Affiche tous les accès actifs
     */
    @FXML
    private void afficherAccesActifs() {
        List<ParentIdentifiantGenere> actifs = ParentIdentifiantService.obtenirAccesActifs();
        tableData.clear();
        tableData.addAll(actifs);
        lblStatut.setText("📊 " + actifs.size() + " accès parent actif(s)");
    }

    /**
     * Génère et imprime les fiches PDF
     */
    @FXML
    private void imprimerEnPdf() {
        if (tableData.isEmpty()) {
            afficherErreur("Aucun identifiant à imprimer. Générez d'abord les identifiants.");
            return;
        }

        // Dialogue de choix du fichier de sortie
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer les fiches PDF");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers PDF (*.pdf)", "*.pdf")
        );

        // Nom par défaut avec timestamp
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
        );
        chooser.setInitialFileName("identifiants_parents_" + timestamp + ".pdf");

        File fichier = chooser.showSaveDialog(btnImprimerPdf.getScene().getWindow());

        if (fichier != null) {
            tâcheEnArrierePlan(() -> {
                try {
                    FicheIdentifiantPdfService.genererPdf(
                        new java.util.ArrayList<>(tableData),
                        obtenirNomEcole(),
                        fichier.getAbsolutePath()
                    );

                    Platform.runLater(() -> {
                        afficherSucces(
                            "PDF généré avec succès !\n" +
                            "Fichier : " + fichier.getAbsolutePath()
                        );
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() ->
                        afficherErreur("Erreur lors de la génération PDF : " + ex.getMessage())
                    );
                }
            });
        }
    }

    /**
     * Récupère le sigle (initiales) de l'établissement pour les identifiants
     */
    private String obtenirSigleEcole() {
        ConfigurationEcole config = ConfigurationEcoleRepository.obtenirConfiguration();
        if (config != null && config.getNomEcole() != null) {
            // Extraire les initiales du nom de l'école
            String[] mots = config.getNomEcole().split("\\s+");
            StringBuilder sigle = new StringBuilder();
            for (String mot : mots) {
                if (!mot.isEmpty()) {
                    sigle.append(mot.charAt(0));
                }
            }
            return sigle.toString().toUpperCase().substring(0, Math.min(4, sigle.length()));
        }
        return "ECOL"; // Fallback
    }

    /**
     * Récupère le nom complet de l'école
     */
    private String obtenirNomEcole() {
        ConfigurationEcole config = ConfigurationEcoleRepository.obtenirConfiguration();
        if (config != null && config.getNomEcole() != null) {
            return config.getNomEcole();
        }
        return "Établissement Scolaire";
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
     * Exécute une tâche en arrière-plan (thread non-UI)
     */
    private void tâcheEnArrierePlan(Runnable task) {
        new Thread(task).start();
    }
}
