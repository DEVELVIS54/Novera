package com.fasodev.gestionscolaire.models;

/**
 * Représente une classe (ex: 6ème A, Terminale D).
 */
public class Classe {

    private int id;
    private String nom;
    private String niveau;
    private boolean estClasseExamen;
    private String nomExamen;
    private boolean finDeParcours;

    public Classe() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }

    public boolean isEstClasseExamen() { return estClasseExamen; }
    public void setEstClasseExamen(boolean estClasseExamen) { this.estClasseExamen = estClasseExamen; }

    public String getNomExamen() { return nomExamen; }
    public void setNomExamen(String nomExamen) { this.nomExamen = nomExamen; }

    public boolean isFinDeParcours() { return finDeParcours; }
    public void setFinDeParcours(boolean finDeParcours) { this.finDeParcours = finDeParcours; }

    @Override
    public String toString() {
        // Utilisé par défaut dans les ComboBox JavaFX
        return nom;
    }
}
