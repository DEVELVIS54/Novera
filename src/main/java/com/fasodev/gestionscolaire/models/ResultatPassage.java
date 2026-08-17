package com.fasodev.gestionscolaire.models;

import java.time.LocalDateTime;

/**
 * Représente le résultat du calcul de passage en classe supérieure pour un étudiant.
 * 
 * Statut calculé :
 *   - ADMIS : moyenne annuelle >= seuil admission → passe automatiquement
 *   - REDOUBLE : moyenne annuelle < seuil redoublement → redouble direct
 *   - DELIBERATION : moyenne entre seuil redoublement et admission → conseil de classe
 * 
 * Statut final : décision du Directeur après délibération (si applicable)
 */
public class ResultatPassage {

    private int id;
    private int etudiantId;
    private String nomEtudiant;
    private String classeOriginNom;
    private String annee_scolaire;
    
    // Moyennes calculées
    private double moyenneT1;
    private double moyenneT2;
    private double moyenneT3;
    private double moyenneAnnuelle;
    
    // Statut calculé automatiquement
    private String statutCalcule;  // ADMIS, REDOUBLE, DELIBERATION
    
    // Statut final (après validation Directeur)
    private String statutFinal;    // ADMIS, REDOUBLE, null (en attente)
    
    // Classe de destination
    private int classeOriginId;
    private int classeSuivanteId;
    private String classeSuivanteNom;
    
    // Audit
    private String valideePar;
    private LocalDateTime dateValidation;
    private LocalDateTime dateCalcul;

    public ResultatPassage() {
    }

    public ResultatPassage(int etudiantId, String nomEtudiant, String classeOriginNom, String annee) {
        this.etudiantId = etudiantId;
        this.nomEtudiant = nomEtudiant;
        this.classeOriginNom = classeOriginNom;
        this.annee_scolaire = annee;
        this.dateCalcul = LocalDateTime.now();
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public String getClasseOriginNom() { return classeOriginNom; }
    public void setClasseOriginNom(String classeOriginNom) { this.classeOriginNom = classeOriginNom; }

    public String getAnnee_scolaire() { return annee_scolaire; }
    public void setAnnee_scolaire(String annee_scolaire) { this.annee_scolaire = annee_scolaire; }

    public double getMoyenneT1() { return moyenneT1; }
    public void setMoyenneT1(double moyenneT1) { this.moyenneT1 = moyenneT1; }

    public double getMoyenneT2() { return moyenneT2; }
    public void setMoyenneT2(double moyenneT2) { this.moyenneT2 = moyenneT2; }

    public double getMoyenneT3() { return moyenneT3; }
    public void setMoyenneT3(double moyenneT3) { this.moyenneT3 = moyenneT3; }

    public double getMoyenneAnnuelle() { return moyenneAnnuelle; }
    public void setMoyenneAnnuelle(double moyenneAnnuelle) { this.moyenneAnnuelle = moyenneAnnuelle; }

    public String getStatutCalcule() { return statutCalcule; }
    public void setStatutCalcule(String statutCalcule) { this.statutCalcule = statutCalcule; }

    public String getStatutFinal() { return statutFinal; }
    public void setStatutFinal(String statutFinal) { this.statutFinal = statutFinal; }

    public int getClasseOriginId() { return classeOriginId; }
    public void setClasseOriginId(int classeOriginId) { this.classeOriginId = classeOriginId; }

    public int getClasseSuivanteId() { return classeSuivanteId; }
    public void setClasseSuivanteId(int classeSuivanteId) { this.classeSuivanteId = classeSuivanteId; }

    public String getClasseSuivanteNom() { return classeSuivanteNom; }
    public void setClasseSuivanteNom(String classeSuivanteNom) { this.classeSuivanteNom = classeSuivanteNom; }

    public String getValideePar() { return valideePar; }
    public void setValideePar(String valideePar) { this.valideePar = valideePar; }

    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }

    public LocalDateTime getDateCalcul() { return dateCalcul; }
    public void setDateCalcul(LocalDateTime dateCalcul) { this.dateCalcul = dateCalcul; }

    /**
     * Retourne un booléen : true si le statut final a été validé
     */
    public boolean isValidé() {
        return statutFinal != null && dateValidation != null;
    }

    @Override
    public String toString() {
        return nomEtudiant + " (" + classeOriginNom + ") - " + statutCalcule;
    }
}
