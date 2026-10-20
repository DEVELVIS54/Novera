package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.repositories.SyncRepository;
import com.fasodev.gestionscolaire.services.SyncScheduler;
import com.fasodev.gestionscolaire.services.SyncService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

/** Écran Directeur : paramètres du serveur et synchronisation manuelle. */
public class SynchronisationController implements Initializable {

    @FXML private TextField urlField;
    @FXML private PasswordField cleField;
    @FXML private Label enAttenteLabel;
    @FXML private Label messageLabel;
    @FXML private Button testerButton;
    @FXML private Button synchroButton;

    private final SyncRepository repository = new SyncRepository();
    private final SyncService service = new SyncService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        SyncRepository.ConfigSync config = repository.lireConfig();
        urlField.setText(config.urlServeur() != null ? config.urlServeur() : "");
        cleField.setText(config.cleApi() != null ? config.cleApi() : "");
        rafraichirCompteurs();
    }

    @FXML
    public void onEnregistrerClick() {
        repository.enregistrerConfig(urlField.getText().trim(), cleField.getText().trim());
        afficher("Paramètres enregistrés.", false);
    }

    @FXML
    public void onTesterClick() {
        String url = urlField.getText().trim();
        String cle = cleField.getText().trim();
        testerButton.setDisable(true);
        afficher("Test en cours…", false);

        executer(() -> {
            String erreur = service.tester(url, cle);
            Platform.runLater(() -> {
                testerButton.setDisable(false);
                if (erreur == null) afficher("Connexion réussie ✅", false);
                else afficher(erreur, true);
            });
        });
    }

    @FXML
    public void onSynchroniserClick() {
        repository.enregistrerConfig(urlField.getText().trim(), cleField.getText().trim());
        repository.reessayerEchecs();
        synchroButton.setDisable(true);
        afficher("Synchronisation en cours…", false);

        executer(() -> {
            SyncService.Resultat r = service.synchroniser();
            Platform.runLater(() -> {
                synchroButton.setDisable(false);
                afficher(r.message(), !r.serveurJoignable());
                rafraichirCompteurs();
            });
            SyncScheduler.declencher();
        });
    }

    private void rafraichirCompteurs() {
        int attente = repository.compterEnAttente();
        int echecs = repository.compterEnEchec();
        enAttenteLabel.setText(attente + " opération(s) en attente"
            + (echecs > 0 ? " — " + echecs + " refusée(s) par le serveur" : ""));
    }

    private void afficher(String texte, boolean erreur) {
        messageLabel.setText(texte);
        messageLabel.setStyle(erreur ? "-fx-text-fill: #b91c1c;" : "-fx-text-fill: #166534;");
    }

    private void executer(Runnable tache) {
        Thread t = new Thread(tache, "novera-sync-manuel");
        t.setDaemon(true);
        t.start();
    }
}
