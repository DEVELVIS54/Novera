package com.fasodev.gestionscolaire.models;

/**
 * Représente un utilisateur du Desktop (Directeur = ADMIN, Secrétaire = USER).
 */
public class Utilisateur {

    private int id;
    private String nomUtilisateur;
    private String passwordHash; // jamais le mot de passe en clair
    private String role;         // "ADMIN" ou "USER"
    private boolean actif;

    public Utilisateur() {
    }

    public Utilisateur(String nomUtilisateur, String passwordHash, String role) {
        this.nomUtilisateur = nomUtilisateur;
        this.passwordHash = passwordHash;
        this.role = role;
        this.actif = true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNomUtilisateur() { return nomUtilisateur; }
    public void setNomUtilisateur(String nomUtilisateur) { this.nomUtilisateur = nomUtilisateur; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public boolean isAdmin() { return "ADMIN".equals(role); }
}
