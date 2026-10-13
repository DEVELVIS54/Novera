package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.ParentIdentifiantGenere;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.FileOutputStream;
import java.util.List;

/**
 * Service de génération PDF pour les fiches d'identifiants parents.
 *
 * Format : A4 portrait, grille 2 colonnes × 5 rangées = 10 fiches par page
 * Chaque fiche contient :
 * - Nom de l'établissement
 * - Nom & classe de l'étudiant
 * - Identifiant (format LSJK-6A-0042)
 * - Mot de passe (6 caractères)
 * - Instructions d'accès au portail
 */
public class FicheIdentifiantPdfService {

    private static final float PAGE_WIDTH = PageSize.A4.getWidth();  // 595.276
    private static final float PAGE_HEIGHT = PageSize.A4.getHeight(); // 841.890
    private static final float MARGIN = 10;
    private static final float FICHE_WIDTH = (PAGE_WIDTH - MARGIN * 3) / 2; // 2 colonnes
    private static final float FICHE_HEIGHT = (PAGE_HEIGHT - MARGIN * 6) / 5; // ~5 rangées

    /**
     * Génère un PDF avec les fiches d'identifiants.
     *
     * @param identifiants      liste des ParentIdentifiantGenere
     * @param nomEcole          nom de l'établissement
     * @param cheminSortie      chemin du fichier PDF à créer
     */
    public static void genererPdf(
        List<ParentIdentifiantGenere> identifiants,
        String nomEcole,
        String cheminSortie
    ) throws Exception {

        PdfWriter writer = new PdfWriter(new FileOutputStream(cheminSortie));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

        // Créer la grille 2 colonnes
        Table grille = new Table(new float[]{FICHE_WIDTH, FICHE_WIDTH});
        grille.setMarginBottom(0);
        grille.setMarginTop(0);
        grille.setMarginLeft(0);
        grille.setMarginRight(0);

        int count = 0;
        for (ParentIdentifiantGenere ident : identifiants) {
            // Créer la fiche
            Cell celleFiche = creerCelleFiche(ident, nomEcole);
            grille.addCell(celleFiche);

            count++;

            // Tous les 10 fiches (5 rangées × 2 colonnes), commencer une nouvelle page
            if (count % 10 == 0) {
                document.add(grille);
                grille = new Table(new float[]{FICHE_WIDTH, FICHE_WIDTH});
                grille.setMarginBottom(0);
                grille.setMarginTop(0);
                grille.setMarginLeft(0);
                grille.setMarginRight(0);
            }
        }

        // Ajouter les fiches restantes
        if (count % 10 != 0) {
            // Remplir les cases vides pour garder le layout cohérent
            int remaining = count % 10;
            while (remaining % 2 != 0) {
                Cell empty = new Cell();
                empty.setHeight(FICHE_HEIGHT);
                grille.addCell(empty);
                remaining++;
            }
            document.add(grille);
        }

        document.close();
    }

    /**
     * Crée une cellule contenant une fiche d'identifiant.
     */
    private static Cell creerCelleFiche(ParentIdentifiantGenere ident, String nomEcole) {
        Cell cell = new Cell();
        cell.setHeight(FICHE_HEIGHT);
        cell.setBorder(new SolidBorder(0.5f));
        cell.setPadding(5);
        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);

        // Contenu de la fiche
        Table ficheTable = new Table(1);
        ficheTable.setWidthPercent(100);

        // En-tête : nom de l'école
        Paragraph headerText = new Paragraph(nomEcole)
            .setFontSize(8)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER);
        ficheTable.addCell(creerCellulesansBordure(headerText));

        // Étudiants info
        Paragraph studentInfo = new Paragraph(
            ident.getNomEtudiant() + " - Classe " + ident.getClasseNom()
        )
            .setFontSize(7)
            .setTextAlignment(TextAlignment.CENTER);
        ficheTable.addCell(creerCellulesansBordure(studentInfo));

        // Identifiant
        Paragraph identLabel = new Paragraph("Identifiant :")
            .setFontSize(6)
            .setBold();
        ficheTable.addCell(creerCellulesansBordure(identLabel));

        Paragraph identValue = new Paragraph(ident.getIdentifiant())
            .setFontSize(9)
            .setBold()
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.BLUE)
            .setTextAlignment(TextAlignment.CENTER);
        ficheTable.addCell(creerCellulesansBordure(identValue));

        // Mot de passe
        Paragraph pwdLabel = new Paragraph("Mot de passe :")
            .setFontSize(6)
            .setBold();
        ficheTable.addCell(creerCellulesansBordure(pwdLabel));

        // Si le mot de passe est "***", ne pas l'afficher en clair (accès regénéré)
        String pwdDisplay = "***".equals(ident.getMotDePasseClair()) ? "[Déjà généré]" : ident.getMotDePasseClair();
        Paragraph pwdValue = new Paragraph(pwdDisplay)
            .setFontSize(9)
            .setBold()
            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.RED)
            .setTextAlignment(TextAlignment.CENTER);
        ficheTable.addCell(creerCellulesansBordure(pwdValue));

        // Instruction
        Paragraph instruction = new Paragraph(
            "Connectez-vous au portail parent avec ces identifiants pour consulter les notes et paiements."
        )
            .setFontSize(5)
            .setTextAlignment(TextAlignment.CENTER);
        ficheTable.addCell(creerCellulesansBordure(instruction));

        cell.add(ficheTable);
        return cell;
    }

    /**
     * Crée une cellule sans bordure pour insérer dans la grille interne.
     */
    private static Cell creerCellulesansBordure(Paragraph paragraph) {
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.add(paragraph);
        return cell;
    }
}
