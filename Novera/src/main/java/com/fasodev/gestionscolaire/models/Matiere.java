package com.fasodev.gestionscolaire.models;

public class Matiere {

    private int id;
    private String nom;
    private String nomProfesseur;
    private double coefficient = 1.0;
    private double baremeMin = 0;
    private double baremeMax = 20;
    private int classeId;
    private String classeNom;

    public Matiere() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getNomProfesseur() { return nomProfesseur; }
    public void setNomProfesseur(String nomProfesseur) { this.nomProfesseur = nomProfesseur; }

    public double getCoefficient() { return coefficient; }
    public void setCoefficient(double coefficient) { this.coefficient = coefficient; }

    public double getBaremeMin() { return baremeMin; }
    public void setBaremeMin(double baremeMin) { this.baremeMin = baremeMin; }

    public double getBaremeMax() { return baremeMax; }
    public void setBaremeMax(double baremeMax) { this.baremeMax = baremeMax; }

    public int getClasseId() { return classeId; }
    public void setClasseId(int classeId) { this.classeId = classeId; }

    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }

    @Override
    public String toString() {
        return nom;
    }
}
