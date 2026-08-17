package com.fasodev.gestionscolaire.database;

import java.io.File;

/**
 * Détermine où stocker les données de l'application selon l'OS
 * (voir doc CONTENU_INSTALLATION_PC.md pour le détail des emplacements).
 *
 * Windows : C:\Users\{user}\AppData\Local\GestionScolaire\
 * macOS   : ~/Library/Application Support/GestionScolaire/
 * Linux   : ~/.local/share/GestionScolaire/
 */
public class AppPaths {

    private static final String APP_FOLDER_NAME = "GestionScolaire";

    public static String getAppDataFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        String basePath;

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            basePath = (localAppData != null ? localAppData : userHome) 
                       + File.separator + APP_FOLDER_NAME;
        } else if (os.contains("mac")) {
            basePath = userHome + "/Library/Application Support/" + APP_FOLDER_NAME;
        } else {
            basePath = userHome + "/.local/share/" + APP_FOLDER_NAME;
        }

        File dossier = new File(basePath);
        if (!dossier.exists()) {
            dossier.mkdirs();
        }

        return basePath;
    }

    public static String getDatabaseFilePath() {
        return getAppDataFolder() + File.separator + "gestion_scolaire.db";
    }

    public static String getBulletinsFolder() {
        return creerSousDossier("bulletins");
    }

    public static String getRecusFolder() {
        return creerSousDossier("recus");
    }

    public static String getFichesIdentifiantsFolder() {
        return creerSousDossier("fiches_identifiants");
    }

    public static String getExportsFolder() {
        return creerSousDossier("exports");
    }

    public static String getLogsFolder() {
        return creerSousDossier("logs");
    }

    private static String creerSousDossier(String nom) {
        String path = getAppDataFolder() + File.separator + nom;
        File dossier = new File(path);
        if (!dossier.exists()) {
            dossier.mkdirs();
        }
        return path;
    }
}
