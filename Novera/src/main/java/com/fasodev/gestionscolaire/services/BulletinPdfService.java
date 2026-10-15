package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.AppPaths;
import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.models.NotesMatiere;
import com.fasodev.gestionscolaire.repositories.CalculMoyenneRepository;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Génère les bulletins de notes d'une classe pour un trimestre :
 * un seul fichier PDF, une page par élève, enregistré dans le dossier
 * "bulletins" de l'application (voir AppPaths).
 *
 * Les moyennes et les rangs affichés sont ceux enregistrés lors du calcul
 * verrouillé du trimestre : le bulletin reflète donc exactement le classement validé.
 */
public class BulletinPdfService {

    private final CalculMoyenneRepository calculRepository = new CalculMoyenneRepository();
    private final NoteService noteService = new NoteService();
    private final ConfigurationEcoleRepository configEcoleRepository = new ConfigurationEcoleRepository();

    public File genererBulletinsClasse(int classeId, int trimestre) {

        if (!"calcule_verrouille".equals(calculRepository.getStatut(classeId, trimestre))) {
            throw new IllegalStateException(
                "Calcule d'abord les moyennes de cette classe pour ce trimestre.");
        }

        Classe classe = ClasseRepository.obtenirParId(classeId);
        List<MoyenneResultat> moyennes = calculRepository.getMoyennes(classeId, trimestre);
        if (classe == null || moyennes.isEmpty()) {
            throw new IllegalStateException("Aucune moyenne enregistrée pour cette classe.");
        }

        ConfigurationEcoleRepository.ConfigEcole ecole = configEcoleRepository.getConfiguration();
        String nomFichier = "bulletins_" + nettoyer(classe.getNom()) + "_T" + trimestre + ".pdf";
        File sortie = new File(AppPaths.getBulletinsFolder(), nomFichier);

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(sortie));
             Document document = new Document(pdf, PageSize.A4)) {

            document.setMargins(40, 40, 40, 40);
            PdfFont gras = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            boolean premiere = true;
            for (MoyenneResultat resultat : moyennes) {
                if (!premiere) {
                    document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }
                premiere = false;

                Etudiant etudiant = EtudiantRepository.obtenirParId(resultat.getEtudiantId());
                List<NotesMatiere> notes = noteService.recupererNotesParMatiere(
                    resultat.getEtudiantId(), classeId, trimestre);

                ajouterBulletin(document, gras, normal, ecole, classe, etudiant, notes,
                    resultat, moyennes.size(), trimestre);
            }

        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le PDF : " + e.getMessage(), e);
        }
        return sortie;
    }

    private void ajouterBulletin(Document document, PdfFont gras, PdfFont normal,
                                 ConfigurationEcoleRepository.ConfigEcole ecole, Classe classe,
                                 Etudiant etudiant, List<NotesMatiere> notes,
                                 MoyenneResultat resultat, int effectif, int trimestre) {

        document.add(new Paragraph(ecole.nom != null ? ecole.nom : "Établissement Scolaire")
            .setFont(gras).setFontSize(16).setTextAlignment(TextAlignment.CENTER));
        if (ecole.adresse != null && !ecole.adresse.isBlank()) {
            document.add(new Paragraph(ecole.adresse)
                .setFont(normal).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        }

        document.add(new Paragraph("BULLETIN DE NOTES — TRIMESTRE " + trimestre)
            .setFont(gras).setFontSize(13).setTextAlignment(TextAlignment.CENTER).setMarginTop(14));

        String nomEleve = etudiant != null ? etudiant.getNomComplet() : resultat.getEtudiantNomComplet();
        String matricule = etudiant != null && etudiant.getMatricule() != null ? etudiant.getMatricule() : "—";

        document.add(new Paragraph("Élève : " + nomEleve).setFont(gras).setFontSize(11).setMarginTop(10));
        document.add(new Paragraph("Matricule : " + matricule + "     Classe : " + classe.getNom())
            .setFont(normal).setFontSize(10));

        Table table = new Table(UnitValue.createPercentArray(new float[] {5, 1.2f, 2}));
        table.setWidth(UnitValue.createPercentValue(100)).setMarginTop(14);
        table.addHeaderCell(entete("Matière", gras));
        table.addHeaderCell(entete("Coef.", gras));
        table.addHeaderCell(entete("Moyenne /20", gras));

        for (NotesMatiere nm : notes) {
            table.addCell(cellule(nm.getMatiereNom(), normal, TextAlignment.LEFT));
            table.addCell(cellule(formater(nm.getCoefficient()), normal, TextAlignment.CENTER));
            table.addCell(cellule(nm.estComplete() || nm.getMoyenneDirecte() != null
                    ? String.format("%.2f", nm.calculerMoyenneMatiere()) : "—",
                normal, TextAlignment.CENTER));
        }
        document.add(table);

        document.add(new Paragraph("Moyenne du trimestre : " + String.format("%.2f", resultat.getMoyenne()) + " / 20")
            .setFont(gras).setFontSize(12).setMarginTop(14));
        document.add(new Paragraph("Rang : " + resultat.getRang() + " / " + effectif)
            .setFont(normal).setFontSize(11));
        document.add(new Paragraph("Appréciation : "
                + MoyenneCalculatorService.appreciation(resultat.getMoyenne()))
            .setFont(normal).setFontSize(11));

        document.add(new Paragraph("Signature du Directeur")
            .setFont(normal).setFontSize(9).setTextAlignment(TextAlignment.RIGHT).setMarginTop(40));
    }

    private Cell entete(String texte, PdfFont gras) {
        return new Cell()
            .add(new Paragraph(texte).setFont(gras).setFontSize(10).setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(new DeviceRgb(226, 232, 240))
            .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
    }

    private Cell cellule(String texte, PdfFont normal, TextAlignment alignement) {
        return new Cell()
            .add(new Paragraph(texte).setFont(normal).setFontSize(10).setTextAlignment(alignement))
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
    }

    private static String formater(double valeur) {
        return valeur == Math.rint(valeur) ? String.valueOf((long) valeur) : String.valueOf(valeur);
    }

    private static String nettoyer(String texte) {
        return texte.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
