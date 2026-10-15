package com.fasodev.gestionscolaire;

import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.services.AuthService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Point d'entrée de l'application Desktop Gestion Scolaire.
 *
 * Cette classe :
 * 1. Initialise la base SQLite locale (si premier lancement)
 * 2. Redirige vers l'écran de configuration initiale (aucun compte encore
 *    créé) OU vers l'écran de connexion (comptes déjà existants)
 */
public class Main extends Application {

    @Override
    public void init() throws Exception {
        // Étape exécutée AVANT l'affichage de l'interface.
        // On s'assure que la base SQLite existe et est à jour.
        DatabaseInitializer.initialiser();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {

        AuthService authService = new AuthService();
        boolean premierLancement = authService.estPremierLancement();

        String vueInitiale = premierLancement ? "/views/setup.fxml" : "/views/login.fxml";

        URL fxmlUrl = getClass().getResource(vueInitiale);
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(
            getClass().getResource("/css/styles.css").toExternalForm()
        );

        primaryStage.setTitle("Gestion Scolaire");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
