package com.fasodev.gestionscolaire.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * GÃ¨re la connexion Ã  la base de donnÃ©es SQLite locale.
 *
 * La base est stockÃ©e dans le dossier de donnÃ©es de l'application
 * (AppData sur Windows), PAS dans le dossier d'installation,
 * pour survivre aux mises Ã  jour du logiciel.
 */
public class DatabaseConnection {

    private static DatabaseConnection instance;
    private final String url;

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
     * RÃ©servÃ© aux tests : redirige l'application vers une autre base (fichier temporaire),
     * sans toucher Ã  la vraie base de l'utilisateur. Ã€ annuler avec {@link #reinitialiserPourTests()}.
     */
    public static synchronized void utiliserBasePourTests(String cheminFichier) {
        instance = new DatabaseConnection("jdbc:sqlite:" + cheminFichier);
    }

    /** RÃ©servÃ© aux tests : revient Ã  la base normale de l'application. */
    public static synchronized void reinitialiserPourTests() {
        instance = null;
    }

    /**
     * Retourne une nouvelle connexion SQLite.
     * SQLite gÃ¨re mal les connexions partagÃ©es entre threads,
     * donc on en ouvre une nouvelle Ã  chaque appel (lÃ©ger, fichier local).
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        // Active les clÃ©s Ã©trangÃ¨res (dÃ©sactivÃ©es par dÃ©faut sous SQLite)
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }
}