package com.fasodev.gestionscolaire.models;

import java.time.LocalDate;

public class Etudiant {

    private int id;
    private String prenom;
    private String nom;
    private LocalDate dateNaissance;
    private int classeId;
    private String classeNom; // pratique pour affichage direct (non stocké tel quel)
    private String matricule;
    private String statutScolarite = "actif"; // "actif" | "parti"
    private LocalDate dateDepart;
    private String raisonDepart;
    private boolean affecteEtat;
    private String palierSubvention; // "CEP" | "BEPC" | null
    private int nombreRedoublements;
    private boolean subventionActive = true;

    public Etudiant() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getNomComplet() { return prenom + " " + nom; }

    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }

    public int getClasseId() { return classeId; }
    public void setClasseId(int classeId) { this.classeId = classeId; }

    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getStatutScolarite() { return statutScolarite; }
    public void setStatutScolarite(String statutScolarite) { this.statutScolarite = statutScolarite; }

    public boolean isActif() { return "actif".equals(statutScolarite); }

    public LocalDate getDateDepart() { return dateDepart; }
    public void setDateDepart(LocalDate dateDepart) { this.dateDepart = dateDepart; }

    public String getRaisonDepart() { return raisonDepart; }
    public void setRaisonDepart(String raisonDepart) { this.raisonDepart = raisonDepart; }

    public boolean isAffecteEtat() { return affecteEtat; }
    public void setAffecteEtat(boolean affecteEtat) { this.affecteEtat = affecteEtat; }

    public String getPalierSubvention() { return palierSubvention; }
    public void setPalierSubvention(String palierSubvention) { this.palierSubvention = palierSubvention; }

    public int getNombreRedoublements() { return nombreRedoublements; }
    public void setNombreRedoublements(int nombreRedoublements) { this.nombreRedoublements = nombreRedoublements; }

    public boolean isSubventionActive() { return subventionActive; }
    public void setSubventionActive(boolean subventionActive) { this.subventionActive = subventionActive; }

    @Override
    public String toString() {
        return getNomComplet();
    }
}
