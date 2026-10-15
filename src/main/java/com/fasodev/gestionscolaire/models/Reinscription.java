package com.fasodev.gestionscolaire.models;

import java.time.LocalDateTime;

/**
 * Représente une demande de réinscription après admission à un examen.
 *
 * Flux :
 *   1. Élève admis à un examen (CEP/BEPC) → statut "en_attente"
 *   2. Parent reçoit notification → doit confirmer réinscription
 *   3. Parent accepte → statut "confirmee" + nouvelle classe
 *   4. Parent refuse ou délai expiré → élève "parti"
 *
 * Cas spécial : Si classe = "Terminale" (fin de parcours) → pas de réinscription,
 *              élève sort automatiquement ("Sorti - Poursuite université")
 */
public class Reinscription {

    private int id;
    private int etudiantId;
    private String nomEtudiant;
    private int resultat_examen_id;  // Lien vers l'examen admis
    private String annee_scolaire;
    
    private int nouvelle_classe_id;  // Où se réinscrire
    private String nouvelle_classe_nom;
    
    private String statut;          // "en_attente" ou "confirmee"
    
    private String confirmee_par;   // Parent (email ou nom)
    private LocalDateTime date_confirmation;
    
    private LocalDateTime created_at;

    public Reinscription() {
    }

    public Reinscription(int etudiantId, String nomEtudiant, int resultat_examen_id, String annee) {
        this.etudiantId = etudiantId;
        this.nomEtudiant = nomEtudiant;
        this.resultat_examen_id = resultat_examen_id;
        this.annee_scolaire = annee;
        this.statut = "en_attente";
        this.created_at = LocalDateTime.now();
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public int getResultat_examen_id() { return resultat_examen_id; }
    public void setResultat_examen_id(int resultat_examen_id) { this.resultat_examen_id = resultat_examen_id; }

    public String getAnnee_scolaire() { return annee_scolaire; }
    public void setAnnee_scolaire(String annee_scolaire) { this.annee_scolaire = annee_scolaire; }

    public int getNouvelle_classe_id() { return nouvelle_classe_id; }
    public void setNouvelle_classe_id(int nouvelle_classe_id) { this.nouvelle_classe_id = nouvelle_classe_id; }

    public String getNouvelle_classe_nom() { return nouvelle_classe_nom; }
    public void setNouvelle_classe_nom(String nouvelle_classe_nom) { this.nouvelle_classe_nom = nouvelle_classe_nom; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getConfirmee_par() { return confirmee_par; }
    public void setConfirmee_par(String confirmee_par) { this.confirmee_par = confirmee_par; }

    public LocalDateTime getDate_confirmation() { return date_confirmation; }
    public void setDate_confirmation(LocalDateTime date_confirmation) { this.date_confirmation = date_confirmation; }

    public LocalDateTime getCreated_at() { return created_at; }
    public void setCreated_at(LocalDateTime created_at) { this.created_at = created_at; }

    /**
     * Retourne true si la réinscription a été confirmée
     */
    public boolean isConfirmee() {
        return "confirmee".equals(statut) && date_confirmation != null;
    }

    /**
     * Affichage du statut pour la table
     */
    public String getAffichageStatut() {
        if ("confirmee".equals(statut)) {
            return "✓ Confirmée";
        } else {
            return "⏳ En attente";
        }
    }

    @Override
    public String toString() {
        return nomEtudiant + " → " + nouvelle_classe_nom + " (" + getAffichageStatut() + ")";
    }
}
