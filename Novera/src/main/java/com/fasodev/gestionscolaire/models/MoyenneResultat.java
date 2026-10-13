package com.fasodev.gestionscolaire.models;

/**
 * Résultat calculé pour un étudiant (moyenne + rang), produit par
 * le calcul manuel des moyennes d'une classe/trimestre.
 */
public class MoyenneResultat {

    private int etudiantId;
    private String etudiantNomComplet;
    private double moyenne;
    private int rang;

    public MoyenneResultat(int etudiantId, String etudiantNomComplet, double moyenne) {
        this.etudiantId = etudiantId;
        this.etudiantNomComplet = etudiantNomComplet;
        this.moyenne = moyenne;
    }

    public int getEtudiantId() { return etudiantId; }
    public String getEtudiantNomComplet() { return etudiantNomComplet; }

    public double getMoyenne() { return moyenne; }

    public int getRang() { return rang; }
    public void setRang(int rang) { this.rang = rang; }
}
