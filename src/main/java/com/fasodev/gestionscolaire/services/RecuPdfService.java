package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.AppPaths;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.Paiement;
import com.fasodev.gestionscolaire.models.SoldeEtudiant;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;

import java.io.File;
import java.io.IOException;

/**
 * Génère un reçu de paiement au format PDF (petit format, type ticket),
 * enregistré dans le dossier "recus" de l'application (voir AppPaths).
 */
public class RecuPdfService {

    private final ConfigurationEcoleRepository configEcoleRepository = new ConfigurationEcoleRepository();

    public File genererRecu(Paiement paiement, Etudiant etudiant, SoldeEtudiant soldeActuel) {

        try {
            ConfigurationEcoleRepository.ConfigEcole ecole = configEcoleRepository.getConfiguration();

            String nomFichier = "recu_" + paiement.getId() + ".pdf";
            String cheminComplet = AppPaths.getRecusFolder() + File.separator + nomFichier;

            PdfWriter writer = new PdfWriter(cheminComplet);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A5);
            document.setMargins(25, 25, 25, 25);

            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontNormal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // ===== En-tête établissement =====
            Paragraph nomEcole = new Paragraph(
                    ecole.nom != null ? ecole.nom : "Établissement Scolaire")
                .setFont(fontBold)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER);
            document.add(nomEcole);

            if (ecole.adresse != null && !ecole.adresse.isBlank()) {
                document.add(new Paragraph(ecole.adresse)
                    .setFont(fontNormal).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));
            }

            if (ecole.telephone != null && !ecole.telephone.isBlank()) {
                document.add(new Paragraph("Tél : " + ecole.telephone)
                    .setFont(fontNormal).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));
            }

            document.add(new LineSeparator(new SolidLine()).setMarginTop(8).setMarginBottom(8));

            // ===== Titre =====
            document.add(new Paragraph("REÇU DE PAIEMENT")
                .setFont(fontBold).setFontSize(13)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

            document.add(new Paragraph("N° " + String.format("%06d", paiement.getId()))
                .setFont(fontNormal).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(12));

            // ===== Informations étudiant =====
            document.add(ligneInfo("Étudiant :", etudiant.getNomComplet(), fontNormal, fontBold));
            document.add(ligneInfo("Matricule :", etudiant.getMatricule(), fontNormal, fontBold));
            document.add(ligneInfo("Classe :", etudiant.getClasseNom(), fontNormal, fontBold));
            document.add(ligneInfo("Date :",
                paiement.getDatePaiement() != null ? paiement.getDatePaiement().toString() : "",
                fontNormal, fontBold));

            document.add(new Paragraph(" ").setMarginTop(6));

            // ===== Montant payé (mis en évidence) =====
            Table montantTable = new Table(UnitValue.createPercentArray(new float[]{1}));
            montantTable.setWidth(UnitValue.createPercentValue(100));

            Cell montantCell = new Cell()
                .add(new Paragraph("Montant payé")
                    .setFont(fontNormal).setFontSize(9).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph(String.format("%,.0f FCFA", paiement.getMontant()))
                    .setFont(fontBold).setFontSize(18))
                .setBackgroundColor(new DeviceRgb(240, 247, 240))
                .setBorder(new SolidBorder(new DeviceRgb(200, 230, 200), 1))
                .setPadding(12)
                .setTextAlignment(TextAlignment.CENTER);

            montantTable.addCell(montantCell);
            document.add(montantTable);

            document.add(new Paragraph(" ").setMarginTop(10));

            // ===== Récapitulatif solde =====
            document.add(ligneInfo("Frais total dû :",
                String.format("%,.0f FCFA", soldeActuel.getFraisReel()), fontNormal, fontBold));

            if (soldeActuel.isSubventionAppliquee()) {
                document.add(new Paragraph(
                    "(Subvention État appliquée : -" +
                    String.format("%.0f", soldeActuel.getPourcentageReduction()) + "%)")
                    .setFont(fontNormal).setFontSize(8).setFontColor(ColorConstants.GRAY));
            }

            document.add(ligneInfo("Total payé à ce jour :",
                String.format("%,.0f FCFA", soldeActuel.getTotalPaye()), fontNormal, fontBold));

            double solde = soldeActuel.getSolde();
            String texteSolde = solde <= 0
                ? "Soldé ✅"
                : String.format("%,.0f FCFA", solde);

            document.add(ligneInfo("Solde restant :", texteSolde, fontNormal, fontBold));

            document.add(new LineSeparator(new SolidLine()).setMarginTop(15).setMarginBottom(8));

            document.add(new Paragraph("Reçu généré automatiquement — Gestion Scolaire")
                .setFont(fontNormal).setFontSize(7)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));

            document.close();

            return new File(cheminComplet);

        } catch (IOException e) {
            throw new RuntimeException("Erreur génération PDF reçu : " + e.getMessage(), e);
        }
    }

    private Paragraph ligneInfo(String label, String valeur, PdfFont fontNormal, PdfFont fontBold) {
        Paragraph p = new Paragraph()
            .add(new com.itextpdf.layout.element.Text(label + " ").setFont(fontNormal).setFontSize(10))
            .add(new com.itextpdf.layout.element.Text(valeur != null ? valeur : "—")
                .setFont(fontBold).setFontSize(10));
        p.setMarginBottom(3);
        return p;
    }

    /**
     * Ouvre le fichier PDF avec l'application par défaut du système
     * (visionneuse PDF installée sur le PC de l'utilisateur).
     */
    public void ouvrirFichier(File fichier) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(fichier);
            }
        } catch (IOException e) {
            // Pas bloquant : le fichier est créé même si l'ouverture auto échoue
            System.out.println("Impossible d'ouvrir automatiquement le PDF : " + e.getMessage());
        }
    }
}
