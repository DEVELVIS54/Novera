package com.fasodev.gestionscolaire.models;

public class FraisScolarite {

    private int id;
    private int classeId;
    private String classeNom;
    private double montant;
    private String description;

    public FraisScolarite() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClasseId() { return classeId; }
    public void setClasseId(int classeId) { this.classeId = classeId; }

    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }

    public double getMontant() { return montant; }
    public void setMontant(double montant) { this.montant = montant; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
