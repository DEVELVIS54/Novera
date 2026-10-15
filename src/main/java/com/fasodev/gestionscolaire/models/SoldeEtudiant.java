package com.fasodev.gestionscolaire.models;

/**
 * Résultat calculé (pas stocké tel quel) résumant la situation
 * financière d'un étudiant : frais réel (après réduction subvention
 * éventuelle), total payé, et solde restant.
 */
public class SoldeEtudiant {

    private double fraisNormal;
    private double fraisReel;   // après réduction subvention État si applicable
    private double totalPaye;
    private boolean subventionAppliquee;
    private double pourcentageReduction;

    public double getFraisNormal() { return fraisNormal; }
    public void setFraisNormal(double fraisNormal) { this.fraisNormal = fraisNormal; }

    public double getFraisReel() { return fraisReel; }
    public void setFraisReel(double fraisReel) { this.fraisReel = fraisReel; }

    public double getTotalPaye() { return totalPaye; }
    public void setTotalPaye(double totalPaye) { this.totalPaye = totalPaye; }

    public double getSolde() { return fraisReel - totalPaye; }

    public boolean isSubventionAppliquee() { return subventionAppliquee; }
    public void setSubventionAppliquee(boolean subventionAppliquee) { this.subventionAppliquee = subventionAppliquee; }

    public double getPourcentageReduction() { return pourcentageReduction; }
    public void setPourcentageReduction(double pourcentageReduction) { this.pourcentageReduction = pourcentageReduction; }

    public String getStatutTexte() {
        double solde = getSolde();
        if (solde <= 0) return "payé";
        if (totalPaye > 0) return "partiellement";
        return "impayé";
    }
}
