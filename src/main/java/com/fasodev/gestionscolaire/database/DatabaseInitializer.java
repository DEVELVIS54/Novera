package com.fasodev.gestionscolaire.database;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Crée le schéma complet de la base SQLite au premier lancement et met à niveau
 * une base existante (migrations historiques idempotentes). Les évolutions
 * futures du schéma passent par {@link SchemaMigrations} (table schema_version).
 */
public class DatabaseInitializer {

    public static void initialiser() {

        boolean premierLancement = !new File(AppPaths.getDatabaseFilePath()).exists();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            // ===================================================
            // Configuration de l'école (mono-établissement : 1 ligne)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS configuration_ecole (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nom TEXT,
                    adresse TEXT,
                    email TEXT,
                    telephone TEXT,
                    logo_path TEXT,
                    seuil_admission REAL DEFAULT 10.0,
                    seuil_redoublement REAL DEFAULT 8.0,
                    api_base_url TEXT,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ===================================================
            // Configuration subvention État (1 ligne)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS configuration_subvention_etat (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reduction_palier_cep REAL DEFAULT 0,
                    reduction_palier_bepc REAL DEFAULT 0,
                    seuil_redoublements_perte INTEGER DEFAULT 2,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ===================================================
            // Utilisateurs Desktop (Admin / User)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS utilisateurs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nom_utilisateur TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT CHECK (role IN ('ADMIN', 'USER')) NOT NULL,
                    actif INTEGER DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ===================================================
            // Classes
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS classes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nom TEXT NOT NULL,
                    niveau TEXT,
                    est_classe_examen INTEGER DEFAULT 0,
                    nom_examen TEXT,
                    fin_de_parcours INTEGER DEFAULT 0,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ===================================================
            // Matières
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS matieres (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nom TEXT NOT NULL,
                    nom_professeur TEXT,
                    coefficient REAL DEFAULT 1.0,
                    bareme_min REAL DEFAULT 0,
                    bareme_max REAL DEFAULT 20,
                    classe_id INTEGER NOT NULL,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (classe_id) REFERENCES classes(id)
                );
            """);

            // ===================================================
            // Étudiants
            // ===================================================
            stmt.execute(ddlEtudiants("etudiants", true));
            migrerTableEtudiants(conn);

            // ===================================================
            // Notes
            // Chaque matière peut recevoir jusqu'à 3 évaluations
            // détaillées (DEVOIR1, DEVOIR2, COMPOSITION) OU une
            // saisie directe de la moyenne (MOYENNE_DIRECTE), qui
            // est alors prioritaire au calcul.
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS notes (
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
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (matiere_id) REFERENCES matieres(id),
                    UNIQUE(etudiant_id, matiere_id, type, trimestre)
                );
            """);

            // ===================================================
            // Calcul des moyennes trimestrielles (manuel + verrouillage)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS calcul_moyennes_trimestre (
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
                    FOREIGN KEY (classe_id) REFERENCES classes(id),
                    UNIQUE(classe_id, trimestre)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS moyennes_trimestrielles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    classe_id INTEGER NOT NULL,
                    trimestre INTEGER CHECK (trimestre BETWEEN 1 AND 3),
                    moyenne REAL,
                    rang INTEGER,
                    calcul_id INTEGER NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (classe_id) REFERENCES classes(id),
                    FOREIGN KEY (calcul_id) REFERENCES calcul_moyennes_trimestre(id),
                    UNIQUE(etudiant_id, trimestre)
                );
            """);

            // ===================================================
            // Résultats annuels (passage en classe supérieure)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS resultats_annuels (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    annee_scolaire TEXT NOT NULL,
                    moyenne_t1 REAL,
                    moyenne_t2 REAL,
                    moyenne_t3 REAL,
                    moyenne_annuelle REAL,
                    statut_calcule TEXT,
                    statut_final TEXT,
                    classe_origine_id INTEGER,
                    classe_suivante_id INTEGER,
                    valide_par TEXT,
                    date_validation DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (classe_origine_id) REFERENCES classes(id),
                    FOREIGN KEY (classe_suivante_id) REFERENCES classes(id)
                );
            """);

            // ===================================================
            // Résultats examen national + réinscriptions
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS resultats_examen_national (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    classe_id INTEGER NOT NULL,
                    annee_scolaire TEXT NOT NULL,
                    nom_examen TEXT,
                    resultat TEXT CHECK (resultat IN ('admis', 'refuse')),
                    decision_si_refuse TEXT CHECK (decision_si_refuse IN ('redouble','parti') OR decision_si_refuse IS NULL),
                    statut_final TEXT,
                    saisi_par TEXT,
                    date_saisie DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                    FOREIGN KEY (classe_id) REFERENCES classes(id),
                    UNIQUE(etudiant_id, annee_scolaire)
                );
            """);

            stmt.execute(ddlReinscriptions("reinscriptions", true));
            migrerTableReinscriptions(conn);

            // ===================================================
            // Frais scolarité & Paiements
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS frais_scolarite (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    classe_id INTEGER NOT NULL,
                    montant REAL NOT NULL,
                    description TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (classe_id) REFERENCES classes(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS paiements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    etudiant_id INTEGER NOT NULL,
                    montant REAL NOT NULL,
                    date_paiement DATE DEFAULT CURRENT_DATE,
                    statut TEXT CHECK (statut IN ('payé', 'partiellement', 'impayé')),
                    notes TEXT,
                    cree_par TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id)
                );
            """);

            // ===================================================
            // Parents (identifiants générés)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS parents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    identifiant TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    etudiant_id INTEGER NOT NULL,
                    date_generation DATETIME DEFAULT CURRENT_TIMESTAMP,
                    genere_par TEXT,
                    imprime INTEGER DEFAULT 0,
                    date_impression DATETIME,
                    actif INTEGER DEFAULT 1,
                    sync_status TEXT DEFAULT 'pending',
                    FOREIGN KEY (etudiant_id) REFERENCES etudiants(id)
                );
            """);

            // ===================================================
            // Audit trail
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_trail (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action TEXT,
                    table_name TEXT,
                    record_id INTEGER,
                    old_value TEXT,
                    new_value TEXT,
                    utilisateur TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // ===================================================
            // Sync Queue (changements en attente d'envoi au serveur)
            // ===================================================
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    operation TEXT CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
                    table_name TEXT NOT NULL,
                    record_id INTEGER NOT NULL,
                    payload TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    synced_at DATETIME,
                    status TEXT DEFAULT 'pending'
                );
            """);

            // ===================================================
            // Index (performance)
            // ===================================================
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_etudiants_classe ON etudiants(classe_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notes_etudiant ON notes(etudiant_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notes_matiere ON notes(matiere_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_paiements_etudiant ON paiements(etudiant_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_parents_identifiant ON parents(identifiant);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_syncqueue_status ON sync_queue(status);");

            // ===================================================
            // MIGRATIONS : ajoute les colonnes manquantes sur une base
            // déjà existante (créée par une version antérieure de l'appli).
            // "CREATE TABLE IF NOT EXISTS" ne modifie JAMAIS une table
            // déjà présente — ces lignes comblent cet écart, SANS perdre
            // les données déjà enregistrées par l'utilisateur.
            // ===================================================
            ajouterColonneSiAbsente(conn, "matieres", "nom_professeur", "TEXT");
            ajouterColonneSiAbsente(conn, "parents", "mot_de_passe_hash", "TEXT");
            ajouterColonneSiAbsente(conn, "parents", "date_utilisation_premiere", "DATETIME");

            // Ligne de configuration par défaut (si pas déjà présente)
            stmt.execute("""
                INSERT INTO configuration_ecole (id, nom)
                SELECT 1, 'Mon Établissement'
                WHERE NOT EXISTS (SELECT 1 FROM configuration_ecole WHERE id = 1);
            """);

            stmt.execute("""
                INSERT INTO configuration_subvention_etat (id)
                SELECT 1
                WHERE NOT EXISTS (SELECT 1 FROM configuration_subvention_etat WHERE id = 1);
            """);

            // ===================================================
            // Synchronisation : chaque INSERT / UPDATE / DELETE sur une table
            // métier est enregistré automatiquement dans sync_queue.
            // ===================================================
            creerTriggersSync(stmt);
            enfilerDonneesExistantes(stmt);

            // Paramètres de connexion au serveur (écran "Synchronisation")
            ajouterColonneSiAbsente(conn, "configuration_ecole", "api_base_url", "TEXT");
            ajouterColonneSiAbsente(conn, "configuration_ecole", "api_key", "TEXT");
            ajouterColonneSiAbsente(conn, "configuration_ecole", "device_id", "TEXT");

            // Versionnement du schéma : applique les migrations numérotées (voir SchemaMigrations)
            SchemaMigrations.appliquer(conn);

            if (premierLancement) {
                System.out.println("✅ Base de données créée : " + AppPaths.getDatabaseFilePath());
            } else {
                System.out.println("✅ Base de données existante chargée : " + AppPaths.getDatabaseFilePath());
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de l'initialisation de la base : " + e.getMessage(), e);
        }
    }

    /** Tables métier envoyées au serveur (les comptes utilisateurs n'en font pas partie). */
    public static final java.util.List<String> TABLES_SYNCHRONISEES = java.util.List.of(
        "configuration_ecole", "configuration_subvention_etat",
        "classes", "matieres", "etudiants", "notes",
        "calcul_moyennes_trimestre", "moyennes_trimestrielles",
        "resultats_annuels", "resultats_examen_national", "reinscriptions",
        "frais_scolarite", "paiements", "parents"
    );

    private static final String[] COLONNES_ETUDIANTS = {
        "id", "prenom", "nom", "date_naissance", "classe_id", "matricule",
        "statut_scolarite", "commentaire_statut", "date_depart", "raison_depart",
        "affecte_etat", "palier_subvention", "nombre_redoublements",
        "subvention_active", "sync_status", "created_at"
    };

    /**
     * Définition de la table etudiants. Le statut accepte 'sorti' et
     * 'en_attente_reinscription' en plus de 'actif' et 'parti', car les
     * modules Passage, Examen national et Réinscriptions les utilisent.
     */
    static String ddlEtudiants(String nomTable, boolean sinonExiste) {
        return ("CREATE TABLE " + (sinonExiste ? "IF NOT EXISTS " : "") + nomTable + " (\n"
            + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + "    prenom TEXT NOT NULL,\n"
            + "    nom TEXT NOT NULL,\n"
            + "    date_naissance DATE,\n"
            + "    classe_id INTEGER NOT NULL,\n"
            + "    matricule TEXT UNIQUE,\n"
            + "    statut_scolarite TEXT DEFAULT 'actif'\n"
            + "        CHECK (statut_scolarite IN ('actif', 'parti', 'sorti', 'en_attente_reinscription')),\n"
            + "    commentaire_statut TEXT,\n"
            + "    date_depart DATE,\n"
            + "    raison_depart TEXT,\n"
            + "    affecte_etat INTEGER DEFAULT 0,\n"
            + "    palier_subvention TEXT CHECK (palier_subvention IN ('CEP','BEPC') OR palier_subvention IS NULL),\n"
            + "    nombre_redoublements INTEGER DEFAULT 0,\n"
            + "    subvention_active INTEGER DEFAULT 1,\n"
            + "    sync_status TEXT DEFAULT 'pending',\n"
            + "    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,\n"
            + "    FOREIGN KEY (classe_id) REFERENCES classes(id)\n"
            + ");");
    }

    /**
     * Met à jour une base créée par une ancienne version : élargit la contrainte
     * CHECK du statut des étudiants (SQLite impose de recréer la table) et ajoute
     * la colonne commentaire_statut. Les données sont conservées.
     */
    static void migrerTableEtudiants(Connection conn) throws SQLException {
        String ddlActuel = null;
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'etudiants'")) {
            if (rs.next()) ddlActuel = rs.getString(1);
        }
        if (ddlActuel == null || ddlActuel.contains("en_attente_reinscription")) {
            ajouterColonneSiAbsente(conn, "etudiants", "commentaire_statut", "TEXT");
            return;
        }

        java.util.Set<String> colonnesExistantes = new java.util.HashSet<>();
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("PRAGMA table_info(etudiants)")) {
            while (rs.next()) colonnesExistantes.add(rs.getString("name").toLowerCase());
        }
        java.util.List<String> aCopier = new java.util.ArrayList<>();
        for (String c : COLONNES_ETUDIANTS) {
            if (colonnesExistantes.contains(c)) aCopier.add(c);
        }
        String liste = String.join(", ", aCopier);

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            conn.setAutoCommit(false);
            try {
                st.execute("DROP TABLE IF EXISTS etudiants_new");
                st.execute(ddlEtudiants("etudiants_new", false));
                st.execute("INSERT INTO etudiants_new (" + liste + ") SELECT " + liste + " FROM etudiants");
                st.execute("DROP TABLE etudiants");
                st.execute("ALTER TABLE etudiants_new RENAME TO etudiants");
                conn.commit();
                System.out.println("🔧 Migration : table \"etudiants\" reconstruite (nouveaux statuts)");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
        recreerIndexEtudiants(conn);
    }

    private static void recreerIndexEtudiants(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_etudiants_classe ON etudiants(classe_id)");
        }
    }

    private static final String COLONNES_REINSCRIPTIONS =
        "id, etudiant_id, resultat_examen_id, nouvelle_classe_id, statut, "
        + "confirmee_par, date_confirmation, annee_scolaire, created_at";

    /**
     * Définition de la table reinscriptions. Le statut accepte 'refusee' en plus de
     * 'en_attente' et 'confirmee', car ReinscriptionRepository.refuser() l'écrit.
     */
    static String ddlReinscriptions(String nomTable, boolean sinonExiste) {
        return """
            CREATE TABLE %s%s (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                etudiant_id INTEGER NOT NULL,
                resultat_examen_id INTEGER NOT NULL,
                nouvelle_classe_id INTEGER,
                statut TEXT DEFAULT 'en_attente' CHECK (statut IN ('en_attente','confirmee','refusee')),
                confirmee_par TEXT,
                date_confirmation DATETIME,
                annee_scolaire TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (etudiant_id) REFERENCES etudiants(id),
                FOREIGN KEY (resultat_examen_id) REFERENCES resultats_examen_national(id),
                FOREIGN KEY (nouvelle_classe_id) REFERENCES classes(id)
            )""".formatted(sinonExiste ? "IF NOT EXISTS " : "", nomTable);
    }

    /**
     * Met à jour une base créée par une ancienne version : élargit la contrainte
     * CHECK du statut des réinscriptions pour accepter 'refusee' (SQLite impose de
     * recréer la table). Les données sont conservées. Les triggers de synchronisation,
     * supprimés avec l'ancienne table, sont recréés plus loin par creerTriggersSync.
     */
    static void migrerTableReinscriptions(Connection conn) throws SQLException {
        String ddlActuel = null;
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'reinscriptions'")) {
            if (rs.next()) ddlActuel = rs.getString(1);
        }
        if (ddlActuel == null || ddlActuel.contains("'refusee'")) {
            return;
        }

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            conn.setAutoCommit(false);
            try {
                st.execute("DROP TABLE IF EXISTS reinscriptions_new");
                st.execute(ddlReinscriptions("reinscriptions_new", false));
                st.execute("INSERT INTO reinscriptions_new (" + COLONNES_REINSCRIPTIONS + ") "
                    + "SELECT " + COLONNES_REINSCRIPTIONS + " FROM reinscriptions");
                st.execute("DROP TABLE reinscriptions");
                st.execute("ALTER TABLE reinscriptions_new RENAME TO reinscriptions");
                conn.commit();
                System.out.println("🔧 Migration : table \"reinscriptions\" reconstruite (statut 'refusee')");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    /** SQL des triggers qui alimentent sync_queue pour une table. */
    static java.util.List<String> sqlTriggersSync(String table) {
        String corps = "INSERT INTO sync_queue (operation, table_name, record_id) VALUES ('%s', '" + table + "', %s.id);";
        return java.util.List.of(
            "CREATE TRIGGER IF NOT EXISTS trg_sync_" + table + "_ins AFTER INSERT ON " + table
                + " BEGIN " + corps.formatted("INSERT", "NEW") + " END;",
            "CREATE TRIGGER IF NOT EXISTS trg_sync_" + table + "_upd AFTER UPDATE ON " + table
                + " BEGIN " + corps.formatted("UPDATE", "NEW") + " END;",
            "CREATE TRIGGER IF NOT EXISTS trg_sync_" + table + "_del AFTER DELETE ON " + table
                + " BEGIN " + corps.formatted("DELETE", "OLD") + " END;"
        );
    }

    static void creerTriggersSync(Statement stmt) throws SQLException {
        for (String table : TABLES_SYNCHRONISEES) {
            for (String sql : sqlTriggersSync(table)) {
                stmt.execute(sql);
            }
        }
    }

    /** Met en file les lignes déjà présentes (données saisies avant l'activation de la synchro). */
    static void enfilerDonneesExistantes(Statement stmt) throws SQLException {
        for (String table : TABLES_SYNCHRONISEES) {
            stmt.execute(
                "INSERT INTO sync_queue (operation, table_name, record_id) "
                + "SELECT 'INSERT', '" + table + "', t.id FROM " + table + " t "
                + "WHERE t.id NOT IN (SELECT record_id FROM sync_queue WHERE table_name = '" + table + "')"
            );
        }
    }

    /**
     * Ajoute une colonne à une table existante si elle n'y figure pas déjà.
     * Permet de faire évoluer le schéma au fil des versions de l'application
     * sans jamais supprimer/recréer la base (donc sans perte de données).
     */
    static void ajouterColonneSiAbsente(Connection conn, String table, String colonne, String type) {
        try (Statement stmt = conn.createStatement()) {

            // Vérifie si la colonne existe déjà (via PRAGMA table_info)
            boolean existe = false;
            var rs = stmt.executeQuery("PRAGMA table_info(" + table + ")");
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(colonne)) {
                    existe = true;
                    break;
                }
            }

            if (!existe) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + colonne + " " + type);
                System.out.println("🔧 Migration : colonne \"" + colonne + "\" ajoutée à \"" + table + "\"");
            }

        } catch (SQLException e) {
            System.out.println(
                "⚠️ Migration échouée pour " + table + "." + colonne + " : " + e.getMessage()
            );
        }
    }
}