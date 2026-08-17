package com.fasodev.gestionscolaire.models;

/**
 * Regroupe les évaluations d'une matière pour un étudiant/trimestre.
 * Deux modes de saisie possibles :
 * - Détaillé : devoir1, devoir2, composition → MD=(D1+D2)/2, puis
 *   moyenne matière = (Composition + MD) / 2
 * - Direct : moyenneDirecte → utilisée telle quelle, toujours prioritaire
 */
public class NotesMatiere {

    private int matiereId;
    private String matiereNom;
    private double coefficient;

    private Double devoir1;
    private Double devoir2;
    private Double composition;
    private Double moyenneDirecte;

    public NotesMatiere(int matiereId, String matiereNom, double coefficient) {
        this.matiereId = matiereId;
        this.matiereNom = matiereNom;
        this.coefficient = coefficient;
    }

    public int getMatiereId() { return matiereId; }
    public String getMatiereNom() { return matiereNom; }
    public double getCoefficient() { return coefficient; }

    public Double getDevoir1() { return devoir1; }
    public void setDevoir1(Double devoir1) { this.devoir1 = devoir1; }

    public Double getDevoir2() { return devoir2; }
    public void setDevoir2(Double devoir2) { this.devoir2 = devoir2; }

    public Double getComposition() { return composition; }
    public void setComposition(Double composition) { this.composition = composition; }

    public Double getMoyenneDirecte() { return moyenneDirecte; }
    public void setMoyenneDirecte(Double moyenneDirecte) { this.moyenneDirecte = moyenneDirecte; }

    public boolean estSaisieDirecte() {
        return moyenneDirecte != null;
    }

    public boolean estComplete() {
        return estSaisieDirecte() || (devoir1 != null && devoir2 != null && composition != null);
    }

    /**
     * Calcule la moyenne de la matière.
     * Priorité : moyenne directe si saisie, sinon formule détaillée.
     */
    public double calculerMoyenneMatiere() {

        if (moyenneDirecte != null) {
            return moyenneDirecte;
        }

        if (devoir1 == null && devoir2 == null && composition == null) {
            return 0;
        }

        double md = moyenneDevoirs();

        if (composition == null) {
            return md;
        }

        return (composition + md) / 2.0;
    }

    private double moyenneDevoirs() {
        if (devoir1 != null && devoir2 != null) {
            return (devoir1 + devoir2) / 2.0;
        } else if (devoir1 != null) {
            return devoir1;
        } else if (devoir2 != null) {
            return devoir2;
        }
        return 0;
    }
}
