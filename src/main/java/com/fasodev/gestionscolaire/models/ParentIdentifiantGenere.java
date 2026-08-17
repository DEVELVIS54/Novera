package com.fasodev.gestionscolaire.models;

/**
 * DTO retourné par la génération d'identifiant parent.
 * Contient le mot de passe EN CLAIR (temporairement, juste le temps de l'afficher/imprimer).
 *
 * À chaque nouvelle instance, c'est un nouveau mdp en clair en mémoire ;
 * en base, on stocke que le hash.
 */
public class ParentIdentifiantGenere {

    private int accesParentId;
    private int etudiantId;
    private String nomEtudiant;
    private String classeNom;
    private String identifiant;      // Ex: LSJK-6A-0042
    private String motDePasseClair;  // 6 caractères, jamais stocké en base

    public ParentIdentifiantGenere() {
    }

    public ParentIdentifiantGenere(
        int accesParentId,
        int etudiantId,
        String nomEtudiant,
        String classeNom,
        String identifiant,
        String motDePasseClair
    ) {
        this.accesParentId = accesParentId;
        this.etudiantId = etudiantId;
        this.nomEtudiant = nomEtudiant;
        this.classeNom = classeNom;
        this.identifiant = identifiant;
        this.motDePasseClair = motDePasseClair;
    }

    public int getAccesParentId() { return accesParentId; }
    public void setAccesParentId(int accesParentId) { this.accesParentId = accesParentId; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public String getClasseNom() { return classeNom; }
    public void setClasseNom(String classeNom) { this.classeNom = classeNom; }

    public String getIdentifiant() { return identifiant; }
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }

    public String getMotDePasseClair() { return motDePasseClair; }
    public void setMotDePasseClair(String motDePasseClair) { this.motDePasseClair = motDePasseClair; }
}
