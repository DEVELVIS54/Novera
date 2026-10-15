package com.fasodev.gestionscolaire.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gère la connexion à la base de données SQLite locale.
 *
 * La base est stockée dans le dossier de données de l'application
 * (AppData sur Windows), PAS dans le dossier d'installation,
 * pour survivre aux mises à jour du logiciel.
 */
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private final String url;

    private DatabaseConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver SQLite introuvable", e);
        }
        this.url = "jdbc:sqlite:" + AppPaths.getDatabaseFilePath();
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Retourne une nouvelle connexion SQLite.
     * SQLite gère mal les connexions partagées entre threads,
     * donc on en ouvre une nouvelle à chaque appel (léger, fichier local).
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        // Active les clés étrangères (désactivées par défaut sous SQLite)
        conn.createStatement().execute("PRAGMA foreign_keys = ON;");
        return conn;
    }
}
