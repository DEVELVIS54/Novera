package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.database.AppPaths;
import com.fasodev.gestionscolaire.services.DashboardService;
import com.fasodev.gestionscolaire.services.SessionManager;
import com.fasodev.gestionscolaire.services.SyncScheduler;
import com.fasodev.gestionscolaire.ui.DashboardView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements javafx.fxml.Initializable {

    @FXML private BorderPane rootPane;
    @FXML private Label userLabel;
    @FXML private Label statusLabel;
    @FXML private Circle syncStatusCircle;
    @FXML private Label syncStatusLabel;
    @FXML private Label pendingSyncLabel;
    @FXML private Button utilisateursButton;
    @FXML private Button synchronisationButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        statusLabel.setText(
            "Base de données locale prête ✅\n" +
            AppPaths.getDatabaseFilePath()
        );

        userLabel.setText("Connecté : " + SessionManager.getNomAffiche());

        // Écrans réservés au Directeur
        boolean admin = SessionManager.getUtilisateurConnecte() != null
            && SessionManager.getUtilisateurConnecte().isAdmin();
        utilisateursButton.setVisible(admin);
        utilisateursButton.setManaged(admin);
        synchronisationButton.setVisible(admin);
        synchronisationButton.setManaged(admin);

        mettreAJourStatutSync(false, 0);
        SyncScheduler.demarrer((online, pending) -> mettreAJourStatutSync(online, pending));

        onTableauDeBordClick();
    }

    @FXML
    public void onTableauDeBordClick() {
        try {
            DashboardService.Resume resume = new DashboardService().charger();
            rootPane.setCenter(DashboardView.creer(resume, SessionManager.getNomAffiche()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            Alert alerte = new Alert(Alert.AlertType.ERROR);
            alerte.setTitle("Tableau de bord");
            alerte.setHeaderText("Impossible de charger les indicateurs");
            alerte.setContentText(e.getMessage());
            alerte.showAndWait();
        }
    }

    @FXML
    public void onClassesClick() {
        naviguerVers("/views/classes.fxml");
    }

    @FXML
    public void onEtudiantsClick() {
        naviguerVers("/views/etudiants.fxml");
    }

    @FXML
    public void onMatieresClick() {
        naviguerVers("/views/matieres.fxml");
    }

    @FXML
    public void onNotesClick() {
        naviguerVers("/views/notes.fxml");
    }

    @FXML
    public void onMoyennesClick() {
        naviguerVers("/views/moyennes.fxml");
    }

    @FXML
    public void onPaiementsClick() {
        naviguerVers("/views/paiements.fxml");
    }

    @FXML
    public void onFraisScolariteClick() {
        naviguerVers("/views/frais_scolarite.fxml");
    }

    @FXML
    public void onIdentifiantsParentsClick() {
        naviguerVers("/views/identifiants_parents.fxml");
    }

    @FXML
    public void onPassageClasseClick() {
        naviguerVers("/views/passage_classe.fxml");
    }

    @FXML
    public void onExamenNationalClick() {
        naviguerVers("/views/examen_national.fxml");
    }

    @FXML
    public void onReinscriptionsClick() {
        naviguerVers("/views/reinscriptions.fxml");
    }

    @FXML
    public void onRechercheClick() {
        naviguerVers("/views/recherche.fxml");
    }

    @FXML
    public void onUtilisateursClick() {
        naviguerVers("/views/utilisateurs.fxml");
    }

    @FXML
    public void onSynchronisationClick() {
        naviguerVers("/views/synchronisation.fxml");
    }

    private void naviguerVers(String cheminFxml) {
        try {
            URL url = getClass().getResource(cheminFxml);

            if (url == null) {
                // Filet de sécurité : getClass().getResource() a échoué
                // (peut arriver selon la config classpath) → on cherche
                // le fichier directement sur le disque, à côté des classes compilées.
                url = trouverRessourceSurDisque(cheminFxml);
            }

            if (url == null) {
                throw new IllegalStateException(
                    "Fichier introuvable ni via classpath ni sur disque : " + cheminFxml
                );
            }

            FXMLLoader loader = new FXMLLoader(url);
            Node vue = loader.load();
            rootPane.setCenter(vue);

        } catch (Exception e) {
            e.printStackTrace();

            Alert alerte = new Alert(Alert.AlertType.ERROR);
            alerte.setTitle("Erreur de chargement");
            alerte.setHeaderText("Impossible d'ouvrir cet écran (" + cheminFxml + ")");
            alerte.setContentText(
                (e.getCause() != null ? e.getCause().toString() : e.toString())
            );
            alerte.showAndWait();
        }
    }

    /**
     * Filet de sécurité : reconstruit le chemin disque réel des classes
     * compilées (target/classes/...) et cherche le fichier FXML directement
     * dedans, si jamais la résolution classpath standard échoue.
     */
    private URL trouverRessourceSurDisque(String cheminFxml) {
        try {
            java.io.File dossierClasses = new java.io.File(
                getClass().getProtectionDomain().getCodeSource().getLocation().toURI()
            );

            java.io.File fichier = new java.io.File(dossierClasses, cheminFxml.replace("/", java.io.File.separator));

            System.out.println("🔍 Recherche fallback sur disque : " + fichier.getAbsolutePath());

            if (fichier.exists()) {
                return fichier.toURI().toURL();
            }

        } catch (Exception ex) {
            System.out.println("Fallback disque échoué : " + ex.getMessage());
        }
        return null;
    }

    public void mettreAJourStatutSync(boolean online, int pendingCount) {
        Platform.runLater(() -> {
            if (online) {
                syncStatusCircle.setStyle("-fx-fill: #2ecc71;");
                syncStatusLabel.setText("En ligne");
            } else {
                syncStatusCircle.setStyle("-fx-fill: #e67e22;");
                syncStatusLabel.setText("Hors ligne");
            }

            pendingSyncLabel.setText(
                pendingCount > 0 ? pendingCount + " en attente de sync" : ""
            );
        });
    }
}
