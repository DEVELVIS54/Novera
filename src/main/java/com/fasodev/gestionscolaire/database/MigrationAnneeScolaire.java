package com.fasodev.gestionscolaire.database;

import com.fasodev.gestionscolaire.utils.AnneeScolaire;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration 2 : introduit l'année scolaire dans les notes, les calculs de moyennes et les
 * moyennes trimestrielles, jusque-là limités à une seule année (contraintes d'unicité sans année).
 *
 * <ul>
 *   <li>ajoute {@code annee_scolaire} aux trois tables et l'inclut dans leurs contraintes UNIQUE ;</li>
 *   <li>rattache toutes les données existantes à l'année fournie (sans rien perdre) ;</li>
 *   <li>ajoute {@code configuration_ecole.annee_scolaire_active} (année en cours de l'école) ;</li>
 *   <li>recrée les index des tables reconstruites et remet leurs lignes en file de synchronisation,
 *       pour que le serveur reçoive la nouvelle colonne.</li>
 * </ul>
 *
 * <p>La colonne a une valeur par défaut (l'année de la migration) pour que le code qui ne la
 * renseigne pas encore continue de fonctionner. Une fois tous les accès à la base passés à
 * l'année explicite, une migration ultérieure retirera cette valeur par défaut.
 */
final class MigrationAnneeScolaire {

    static final int VERSION = 2;

    private static final String COLONNES_NOTES =
        "id, etudiant_id, matiere_id, type, valeur, trimestre, date_saisie, cree_par, sync_status, created_at";
    private static final String COLONNES_CALCULS =
        "id, classe_id, trimestre, statut, calcule_par, date_calcul, date_deverrouillage, "
            + "raison_deverrouillage, created_at";
    private static final String COLONNES_MOYENNES =
        "id, etudiant_id, classe_id, trimestre, moyenne, rang, calcul_id, created_at";

    private MigrationAnneeScolaire() {
    }

    /** @param anneeParDefaut année à laquelle rattacher les données déjà présentes (ex. 2026-2027) */
    static SchemaMigrations.Migration migration(String anneeParDefaut) {
        if (!AnneeScolaire.estValide(anneeParDefaut)) {
            throw new IllegalArgumentException("Année scolaire invalide : " + anneeParDefaut);
        }
        return new SchemaMigrations.Migration(
            VERSION,
            "Année scolaire sur notes, calculs et moyennes trimestrielles",
            conn -> appliquer(conn, anneeParDefaut));
    }

    private static void appliquer(Connection conn, String annee) throws SQLException {
        ajouterAnneeActive(conn, annee);

        // Ordre important : calcul_moyennes_trimestre est référencée par moyennes_trimestrielles.
        reconstruire(conn, "notes", COLONNES_NOTES, ddlNotes("notes_new", annee), annee);
        reconstruire(conn, "calcul_moyennes_trimestre", COLONNES_CALCULS,
            ddlCalculs("calcul_moyennes_trimestre_new", annee), annee);
        reconstruire(conn, "moyennes_trimestrielles", COLONNES_MOYENNES,
            ddlMoyennes("moyennes_trimestrielles_new", annee), annee);

        // Les index et triggers d'une table disparaissent avec elle : on remet les index ici,
        // les triggers de synchronisation sont recréés par DatabaseInitializer juste après.
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_etudiant ON notes(etudiant_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_matiere ON notes(matiere_id)");
        }

        for (String table : new String[] {"notes", "calcul_moyennes_trimestre", "moyennes_trimestrielles"}) {
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO sync_queue (operation, table_name, record_id) "
                    + "SELECT 'UPDATE', '" + table + "', id FROM " + table);
            }
        }
    }

    private static void ajouterAnneeActive(Connection conn, String annee) throws SQLException {
        if (!colonneExiste(conn, "configuration_ecole", "annee_scolaire_active")) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE configuration_ecole ADD COLUMN annee_scolaire_active TEXT");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE configuration_ecole SET annee_scolaire_active = ? "
                    + "WHERE annee_scolaire_active IS NULL OR annee_scolaire_active = ''")) {
            ps.setString(1, annee);
            ps.executeUpdate();
        }
    }

    /** Recrée la table avec la nouvelle structure en conservant toutes les lignes (mêmes id). */
    private static void reconstruire(Connection conn, String table, String colonnes,
                                     String ddlTemporaire, String annee) throws SQLException {
        if (colonneExiste(conn, table, "annee_scolaire")) {
            return;
        }
        String temporaire = table + "_new";

        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + temporaire);
            st.execute(ddlTemporaire);
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + temporaire + " (" + colonnes + ", annee_scolaire) "
                    + "SELECT " + colonnes + ", ? FROM " + table)) {
            ps.setString(1, annee);
            ps.executeUpdate();
        }
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE " + table);
            st.execute("ALTER TABLE " + temporaire + " RENAME TO " + table);
        }
    }

    private static boolean colonneExiste(Connection conn, String table, String colonne) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (colonne.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    // L'année est validée (AAAA-AAAA) avant d'arriver ici : elle peut être écrite dans le DDL.

    static String ddlNotes(String nomTable, String annee) {
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
                annee_scolaire TEXT NOT NULL DEFAULT '%s',
                FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                FOREIGN KEY (matiere_id) REFERENCES matieres(id),
                UNIQUE(etudiant_id, matiere_id, type, trimestre, annee_scolaire)
            )""".formatted(nomTable, annee);
    }

    static String ddlCalculs(String nomTable, String annee) {
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
                annee_scolaire TEXT NOT NULL DEFAULT '%s',
                FOREIGN KEY (classe_id) REFERENCES classes(id),
                UNIQUE(classe_id, trimestre, annee_scolaire)
            )""".formatted(nomTable, annee);
    }

    static String ddlMoyennes(String nomTable, String annee) {
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
                annee_scolaire TEXT NOT NULL DEFAULT '%s',
                FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                FOREIGN KEY (classe_id) REFERENCES classes(id),
                FOREIGN KEY (calcul_id) REFERENCES calcul_moyennes_trimestre(id),
                UNIQUE(etudiant_id, trimestre, annee_scolaire)
            )""".formatted(nomTable, annee);
    }
}