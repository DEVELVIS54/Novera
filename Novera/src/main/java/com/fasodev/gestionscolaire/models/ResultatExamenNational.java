package com.fasodev.gestionscolaire.models;

import java.time.LocalDateTime;

/**
 * Représente le résultat d'un examen national (CEP, BEPC, BAC).
 *
 * Logique :
 *   - ADMIS → Élève passe (peut se réinscrire ou partir selon classe)
 *   - REFUSÉ → Élève échoue (peut redoubler ou partir)
 *
 * Statut final déterminé après la Secrétaire saisit la décision (Redouble/Parti)
 */
public class ResultatExamenNational {

    private int id;
    private int etudiantId;
    private String nomEtudiant;
    private int classeId;
    private String classeNom;
    private String annee_scolaire;
    
    private String nomExamen;      // CEP, BEPC, BAC (du paramétrage classe)
    private String resultat;       // "admis" ou "refuse"
    private String decision_si_refuse;  // "redouble" ou "parti" (null si admis)
    private String statut_final;   // Valeur finalisée après application
    
    private String saisi_par;      // Qui a saisi (Secrétaire)
    private LocalDateTime date_saisie;

    public ResultatExamenNational() {
    }

    public ResultatExamenNational(int etudiantId, String nomEtudiant, 
                                  int classeId, String classeNom,
                                  String annee, String nomExamen) {
        this.etudiantId = etudiantId;
        this.nomEtudiant = nomEtudiant;
        this.classeId = classeId;
        this.classeNom = classeNom;
        this.annee_scolaire = annee;
        this.nomExamen = nomExamen;
        this.date_saisie = LocalDateTime.now();
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public int getClasseId() { return classeId; }
    public void setClasseId(int classeId) { this.classeId = classeId; }

    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }

    public String getAnnee_scolaire() { return annee_scolaire; }
    public void setAnnee_scolaire(String annee_scolaire) { this.annee_scolaire = annee_scolaire; }

    public String getNomExamen() { return nomExamen; }
    public void setNomExamen(String nomExamen) { this.nomExamen = nomExamen; }

    public String getResultat() { return resultat; }
    public void setResultat(String resultat) { this.resultat = resultat; }

    public String getDecision_si_refuse() { return decision_si_refuse; }
    public void setDecision_si_refuse(String decision_si_refuse) { this.decision_si_refuse = decision_si_refuse; }

    public String getStatut_final() { return statut_final; }
    public void setStatut_final(String statut_final) { this.statut_final = statut_final; }

    public String getSaisi_par() { return saisi_par; }
    public void setSaisi_par(String saisi_par) { this.saisi_par = saisi_par; }

    public LocalDateTime getDate_saisie() { return date_saisie; }
    public void setDate_saisie(LocalDateTime date_saisie) { this.date_saisie = date_saisie; }

    /**
     * Retourne true si le résultat a été finalisé
     */
    public boolean isFinalisé() {
        return statut_final != null;
    }

    /**
     * Retourne le statut affiché (pour la table)
     */
    public String getAffichageResultat() {
        if ("admis".equals(resultat)) {
            return "✓ ADMIS";
        } else if ("refuse".equals(resultat)) {
            if ("redouble".equals(decision_si_refuse)) {
                return "✗ REFUSÉ (Redouble)";
            } else if ("parti".equals(decision_si_refuse)) {
                return "✗ REFUSÉ (Parti)";
            } else {
                return "✗ REFUSÉ (?)";
            }
        }
        return "?";
    }

    @Override
    public String toString() {
        return nomEtudiant + " - " + nomExamen + " : " + getAffichageResultat();
    }
}
