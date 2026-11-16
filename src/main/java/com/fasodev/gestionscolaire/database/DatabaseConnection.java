package com.fasodev.gestionscolaire.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    /** Adresse JDBC de la base r~u00E9ellement utilis~u00E9e (messages de d~u00E9marrage, diagnostic). */
    public String getUrl() {
        return url;
    }

    private DatabaseConnection() {
        this("jdbc:sqlite:" + AppPaths.getDatabaseFilePath());
    }

    private DatabaseConnection(String url) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver SQLite introuvable", e);
        }
        this.url = url;
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Réservé aux tests : redirige l'application vers une autre base (fichier temporaire),
     * sans toucher à la vraie base de l'utilisateur. À annuler avec {@link #reinitialiserPourTests()}.
     */
    public static synchronized void utiliserBasePourTests(String cheminFichier) {
        instance = new DatabaseConnection("jdbc:sqlite:" + cheminFichier);
    }

    /** Réservé aux tests : revient à la base normale de l'application. */
    public static synchronized void reinitialiserPourTests() {
        instance = null;
    }

    /**
     * Retourne une nouvelle connexion SQLite.
     * SQLite gère mal les connexions partagées entre threads,
     * donc on en ouvre une nouvelle à chaque appel (léger, fichier local).
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        // Active les clés étrangères (désactivées par défaut sous SQLite)
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }
}