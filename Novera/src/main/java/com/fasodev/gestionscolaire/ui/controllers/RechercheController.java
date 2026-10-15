package com.fasodev.gestionscolaire.ui.controllers;

import com.fasodev.gestionscolaire.services.RechercheService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

/** Recherche rapide d'un élève ou d'une classe. */
public class RechercheController implements Initializable {

    @FXML private TextField rechercheField;
    @FXML private Label resumeLabel;

    @FXML private TableView<RechercheService.EleveTrouve> elevesTable;
    @FXML private TableColumn<RechercheService.EleveTrouve, String> colMatricule;
    @FXML private TableColumn<RechercheService.EleveTrouve, String> colEleve;
    @FXML private TableColumn<RechercheService.EleveTrouve, String> colClasseEleve;
    @FXML private TableColumn<RechercheService.EleveTrouve, String> colStatut;

    @FXML private TableView<RechercheService.ClasseTrouvee> classesTable;
    @FXML private TableColumn<RechercheService.ClasseTrouvee, String> colClasse;
    @FXML private TableColumn<RechercheService.ClasseTrouvee, String> colNiveau;
    @FXML private TableColumn<RechercheService.ClasseTrouvee, String> colEffectif;

    private final RechercheService service = new RechercheService();

    /** Texte à afficher à l'ouverture (utilisé par la palette de commandes). */
    private static String requeteInitiale;

    public static void preremplir(String texte) {
        requeteInitiale = texte;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colMatricule.setCellValueFactory(c -> new SimpleStringProperty(nonNul(c.getValue().matricule())));
        colEleve.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nomComplet()));
        colClasseEleve.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().classe()));
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().statut()));

        colClasse.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nom()));
        colNiveau.setCellValueFactory(c -> new SimpleStringProperty(nonNul(c.getValue().niveau())));
        colEffectif.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().effectif())));

        if (requeteInitiale != null) {
            rechercheField.setText(requeteInitiale);
            requeteInitiale = null;
        }
        rechercheField.textProperty().addListener((obs, ancien, nouveau) -> lancer());
        lancer();
    }

    @FXML
    public void onRechercherClick() {
        lancer();
    }

    private void lancer() {
        String texte = rechercheField.getText();
        var eleves = service.chercherEleves(texte);
        var classes = service.chercherClasses(texte);
        elevesTable.setItems(FXCollections.observableArrayList(eleves));
        classesTable.setItems(FXCollections.observableArrayList(classes));
        resumeLabel.setText(eleves.size() + " élève(s), " + classes.size() + " classe(s)");
    }

    private static String nonNul(String s) {
        return s != null ? s : "";
    }
}
