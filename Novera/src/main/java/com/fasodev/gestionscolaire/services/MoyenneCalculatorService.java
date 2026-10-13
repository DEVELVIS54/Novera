package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.NotesMatiere;

import java.util.List;

/**
 * Calcul pur (aucun accès base) de la moyenne trimestrielle,
 * à partir des moyennes déjà déterminées par matière (voir
 * NotesMatiere.calculerMoyenneMatiere() pour la formule détaillée).
 */
public class MoyenneCalculatorService {

    public static double calculerMoyenneTrimestre(List<NotesMatiere> notesParMatiere) {

        double sommePonderee = 0;
        double sommeCoefficients = 0;

        for (NotesMatiere nm : notesParMatiere) {
            double moyenneMatiere = nm.calculerMoyenneMatiere();
            sommePonderee += moyenneMatiere * nm.getCoefficient();
            sommeCoefficients += nm.getCoefficient();
        }

        return sommeCoefficients > 0 ? sommePonderee / sommeCoefficients : 0;
    }

    /** Appréciation littérale associée à une moyenne sur 20. */
    public static String appreciation(double moyenne) {
        if (moyenne >= 16) return "Très bien";
        if (moyenne >= 14) return "Bien";
        if (moyenne >= 12) return "Assez bien";
        if (moyenne >= 10) return "Passable";
        return "Insuffisant";
    }
}
