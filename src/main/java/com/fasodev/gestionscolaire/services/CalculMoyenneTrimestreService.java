package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.MoyenneResultat;
import com.fasodev.gestionscolaire.models.NotesMatiere;
import com.fasodev.gestionscolaire.repositories.CalculMoyenneRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CalculMoyenneTrimestreService {

    private final CalculMoyenneRepository calculMoyenneRepository = new CalculMoyenneRepository();
    private final EtudiantRepository etudiantRepository = new EtudiantRepository();
    private final NoteService noteService = new NoteService();

    public String getStatut(int classeId, int trimestre) {
        String statut = calculMoyenneRepository.getStatut(classeId, trimestre);
        return statut != null ? statut : "non_calcule";
    }

    public List<MoyenneResultat> calculerMoyennesClasse(int classeId, int trimestre, String utilisateur) {

        if ("calcule_verrouille".equals(getStatut(classeId, trimestre))) {
            throw new IllegalStateException(
                "Les moyennes sont déjà calculées et verrouillées pour ce trimestre."
            );
        }

        List<Etudiant> etudiants = etudiantRepository.findByClasseId(classeId);
        List<MoyenneResultat> resultats = new ArrayList<>();

        for (Etudiant etudiant : etudiants) {
            List<NotesMatiere> notesParMatiere =
                noteService.recupererNotesParMatiere(etudiant.getId(), classeId, trimestre);

            double moyenne = MoyenneCalculatorService.calculerMoyenneTrimestre(notesParMatiere);

            resultats.add(new MoyenneResultat(etudiant.getId(), etudiant.getNomComplet(), moyenne));
        }

        resultats.sort(Comparator.comparingDouble(MoyenneResultat::getMoyenne).reversed());
        for (int i = 0; i < resultats.size(); i++) {
            resultats.get(i).setRang(i + 1);
        }

        int calculId = calculMoyenneRepository.obtenirOuCreerCalculId(classeId, trimestre);
        calculMoyenneRepository.supprimerMoyennesExistantes(calculId);
        calculMoyenneRepository.sauvegarderMoyennes(calculId, classeId, trimestre, resultats);
        calculMoyenneRepository.verrouiller(calculId, utilisateur);

        return resultats;
    }

    public List<MoyenneResultat> getMoyennesCalculees(int classeId, int trimestre) {
        return calculMoyenneRepository.getMoyennes(classeId, trimestre);
    }

    public void deverrouiller(int classeId, int trimestre, String raison) {
        if (raison == null || raison.isBlank()) {
            throw new IllegalArgumentException("La raison du déverrouillage est obligatoire.");
        }
        calculMoyenneRepository.deverrouiller(classeId, trimestre, raison);
    }
}
