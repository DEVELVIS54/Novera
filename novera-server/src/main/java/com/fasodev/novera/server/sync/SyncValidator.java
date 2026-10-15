package com.fasodev.novera.server.sync;

import java.util.Set;

/** Règles de validation d'une opération reçue du desktop (logique pure, sans base ni Spring). */
public final class SyncValidator {

    /** Tables que le desktop est autorisé à synchroniser. */
    public static final Set<String> TABLES_AUTORISEES = Set.of(
        "configuration_ecole", "configuration_subvention_etat",
        "classes", "matieres", "etudiants", "notes",
        "calcul_moyennes_trimestre", "moyennes_trimestrielles",
        "resultats_annuels", "resultats_examen_national", "reinscriptions",
        "frais_scolarite", "paiements", "parents"
    );

    private static final Set<String> OPERATIONS = Set.of("INSERT", "UPDATE", "DELETE");

    private SyncValidator() {}

    /** Retourne null si l'opération est valide, sinon le motif du refus. */
    public static String valider(String table, String operation, long recordId, boolean aUnPayload) {
        if (table == null || !TABLES_AUTORISEES.contains(table)) {
            return "Table non autorisée : " + table;
        }
        if (operation == null || !OPERATIONS.contains(operation)) {
            return "Opération inconnue : " + operation;
        }
        if (recordId <= 0) {
            return "Identifiant d'enregistrement invalide";
        }
        if (!"DELETE".equals(operation) && !aUnPayload) {
            return "Contenu manquant pour " + operation;
        }
        return null;
    }
}
