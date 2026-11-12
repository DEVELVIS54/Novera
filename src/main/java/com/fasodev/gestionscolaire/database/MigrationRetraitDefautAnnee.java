package com.fasodev.gestionscolaire.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 3 : rend l'annÃ©e scolaire obligatoire dans les notes, les calculs de moyennes et
 * les moyennes trimestrielles, en retirant la valeur par dÃ©faut posÃ©e par la migration 2.
 *
 * <p>Depuis le lot Â« annÃ©e active Â», tous les accÃ¨s Ã  ces tables renseignent l'annÃ©e. Sans valeur
 * par dÃ©faut, un oubli futur Ã©choue clairement au lieu de ranger la donnÃ©e dans la mauvaise annÃ©e.
 * Les donnÃ©es et leurs identifiants sont conservÃ©s (SQLite impose de recrÃ©er les tables).
 */
final class MigrationRetraitDefautAnnee {

    static final int VERSION = 3;

    private static final String COLONNES_NOTES =
        "id, etudiant_id, matiere_id, type, valeur, trimestre, date_saisie, cree_par, sync_status, "
            + "created_at, annee_scolaire";
    private static final String COLONNES_CALCULS =
        "id, classe_id, trimestre, statut, calcule_par, date_calcul, date_deverrouillage, "
            + "raison_deverrouillage, created_at, annee_scolaire";
    private static final String COLONNES_MOYENNES =
        "id, etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id, created_at, annee_scolaire";

    private MigrationRetraitDefautAnnee() {
    }

    static SchemaMigrations.Migration migration() {
        return new SchemaMigrations.Migration(
            VERSION,
            "AnnÃ©e scolaire obligatoire (retrait de la valeur par dÃ©faut)",
            MigrationRetraitDefautAnnee::appliquer);
    }

    private static void appliquer(Connection conn) throws SQLException {
        // MÃªme ordre que la migration 2 : calcul_moyennes_trimestre est rÃ©fÃ©rencÃ©e par les moyennes.
        reconstruire(conn, "notes", COLONNES_NOTES, ddlNotes("notes_new"));
        reconstruire(conn, "calcul_moyennes_trimestre", COLONNES_CALCULS,
            ddlCalculs("calcul_moyennes_trimestre_new"));
        reconstruire(conn, "moyennes_trimestrielles", COLONNES_MOYENNES,
            ddlMoyennes("moyennes_trimestrielles_new"));

        // Les index disparaissent avec la table ; les triggers de synchronisation sont recrÃ©Ã©s
        // par DatabaseInitializer juste aprÃ¨s les migrations.
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_etudiant ON notes(etudiant_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_matiere ON notes(matiere_id)");
        }
    }

    private static void reconstruire(Connection conn, String table, String colonnes,
                                     String ddlTemporaire) throws SQLException {
        if (!aUneValeurParDefaut(conn, table)) {
            return;
        }
        String temporaire = table + "_new";

        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + temporaire);
            st.execute(ddlTemporaire);
            st.execute("INSERT INTO " + temporaire + " (" + colonnes + ") "
                + "SELECT " + colonnes + " FROM " + table);
            st.execute("DROP TABLE " + table);
            st.execute("ALTER TABLE " + temporaire + " RENAME TO " + table);
        }
    }

    /** Vrai si la colonne annee_scolaire de la table porte encore une valeur par dÃ©faut. */
    private static boolean aUneValeurParDefaut(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String ddl = rs.getString(1);
                return ddl != null && ddl.contains("annee_scolaire TEXT NOT NULL DEFAULT");
            }
        }
    }

    // MÃªmes tables que MigrationAnneeScolaire, sans valeur par dÃ©faut sur annee_scolaire.

    static String ddlNotes(String nomTable) {
        return """
            CREATE TABLE %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                etudiant_id INTEGER NOT NULL,
                matiere_id INTEGER NOT NULL,
                type TEXT CHECK (type IN ('DEVOIR1', 'DEVOIR2', 'COMPOSITION', 'MOYENNE_DIRECTE')) NOT NULL,
                valeur REAL,
                trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                date_saisie DATE DEFAULT CURRENT_DATE,
                cree_par TEXT,
                sync_status TEXT DEFAULT 'pending',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                annee_scolaire TEXT NOT NULL,
                FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                FOREIGN KEY (matiere_id) REFERENCES matieres(id),
                UNIQUE(etudiant_id, matiere_id, type, trimestre, annee_scolaire)
            )""".formatted(nomTable);
    }

    static String ddlCalculs(String nomTable) {
        return """
            CREATE TABLE %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                classe_id INTEGER NOT NULL,
                trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                statut TEXT DEFAULT 'non_calcule'
                    CHECK (statut IN ('non_calcule', 'calcule_verrouille')),
                calcule_par TEXT,
                date_calcul DATETIME,
                date_deverrouillage DATETIME,
                raison_deverrouillage TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                annee_scolaire TEXT NOT NULL,
                FOREIGN KEY (classe_id) REFERENCES classes(id),
                UNIQUE(classe_id, trimestre, annee_scolaire)
            )""".formatted(nomTable);
    }

    static String ddlMoyennes(String nomTable) {
        return """
            CREATE TABLE %s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                etudiant_id INTEGER NOT NULL,
                classe_id INTEGER NOT NULL,
                trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                moyenne REAL,
                rang INTEGER,
                calcul_id INTEGER NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                annee_scolaire TEXT NOT NULL,
                FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                FOREIGN KEY (classe_id) REFERENCES classes(id),
                FOREIGN KEY (calcul_id) REFERENCES calcul_moyennes_trimestre(id),
                UNIQUE(etudiant_id, trimestre, annee_scolaire)
            )""".formatted(nomTable);
    }
}