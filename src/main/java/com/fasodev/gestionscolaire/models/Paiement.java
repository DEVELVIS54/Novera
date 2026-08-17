package com.fasodev.gestionscolaire.models;

import java.time.LocalDate;

public class Paiement {

    private int id;
    private int etudiantId;
    private double montant;
    private LocalDate datePaiement;
    private String statut; // "payé" | "partiellement" | "impayé" — statut informatif au moment du paiement
    private String notes;
    private String creePar;

    public Paiement() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public double getMontant() { return montant; }
    public void setMontant(double montant) { this.montant = montant; }

    public LocalDate getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDate datePaiement) { this.datePaiement = datePaiement; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
}
