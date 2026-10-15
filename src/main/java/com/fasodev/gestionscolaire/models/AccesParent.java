package com.fasodev.gestionscolaire.models;

import java.time.LocalDateTime;

/**
 * Modèle représentant un identifiant d'accès généré pour un parent
 * (couple identifiant + mot de passe hash).
 *
 * Stocké en base pour tracer les générations et permettre les regénérations.
 */
public class AccesParent {

    private int id;
    private int etudiantId;
    private String identifiant;  // Ex: LSJK-6A-0042
    private String passwordHash; // Hash BCrypt
    private LocalDateTime dateGeneration;
    private LocalDateTime dateUtilisationPremiere; // Null jusqu'à la 1ère connexion parent
    private boolean actif = true;

    public AccesParent() {
    }

    public AccesParent(int etudiantId, String identifiant, String passwordHash) {
        this.etudiantId = etudiantId;
        this.identifiant = identifiant;
        this.passwordHash = passwordHash;
        this.dateGeneration = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getIdentifiant() { return identifiant; }
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDateTime getDateGeneration() { return dateGeneration; }
    public void setDateGeneration(LocalDateTime dateGeneration) { this.dateGeneration = dateGeneration; }

    public LocalDateTime getDateUtilisationPremiere() { return dateUtilisationPremiere; }
    public void setDateUtilisationPremiere(LocalDateTime dateUtilisationPremiere) {
        this.dateUtilisationPremiere = dateUtilisationPremiere;
    }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    @Override
    public String toString() {
        return identifiant;
    }
}
