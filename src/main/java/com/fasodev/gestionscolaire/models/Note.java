package com.fasodev.gestionscolaire.models;

import java.time.LocalDate;

/**
 * Une note représente UNE évaluation pour un étudiant, une matière,
 * un trimestre donné. Le type précise s'il s'agit d'un devoir, d'une
 * composition, ou d'une moyenne saisie directement par l'enseignant.
 */
public class Note {

    public static final String TYPE_DEVOIR1 = "DEVOIR1";
    public static final String TYPE_DEVOIR2 = "DEVOIR2";
    public static final String TYPE_COMPOSITION = "COMPOSITION";
    public static final String TYPE_MOYENNE_DIRECTE = "MOYENNE_DIRECTE";

    private int id;
    private int etudiantId;
    private int matiereId;
    private String type;
    private double valeur;
    private int trimestre;
    private LocalDate dateSaisie;
    private String creePar;

    public Note() {}

    public Note(int etudiantId, int matiereId, String type, double valeur, int trimestre) {
        this.etudiantId = etudiantId;
        this.matiereId = matiereId;
        this.type = type;
        this.valeur = valeur;
        this.trimestre = trimestre;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public int getMatiereId() { return matiereId; }
    public void setMatiereId(int matiereId) { this.matiereId = matiereId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getValeur() { return valeur; }
    public void setValeur(double valeur) { this.valeur = valeur; }

    public int getTrimestre() { return trimestre; }
    public void setTrimestre(int trimestre) { this.trimestre = trimestre; }

    public LocalDate getDateSaisie() { return dateSaisie; }
    public void setDateSaisie(LocalDate dateSaisie) { this.dateSaisie = dateSaisie; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}
