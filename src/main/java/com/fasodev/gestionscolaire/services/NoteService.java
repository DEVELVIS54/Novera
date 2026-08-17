package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Matiere;
import com.fasodev.gestionscolaire.models.Note;
import com.fasodev.gestionscolaire.models.NotesMatiere;
import com.fasodev.gestionscolaire.repositories.CalculMoyenneRepository;
import com.fasodev.gestionscolaire.repositories.MatiereRepository;
import com.fasodev.gestionscolaire.repositories.NoteRepository;

import java.util.List;

public class NoteService {

    private final NoteRepository noteRepository = new NoteRepository();
    private final MatiereRepository matiereRepository = new MatiereRepository();
    private final CalculMoyenneRepository calculMoyenneRepository = new CalculMoyenneRepository();

    /**
     * Saisit une évaluation : DEVOIR1, DEVOIR2, COMPOSITION, ou MOYENNE_DIRECTE.
     */
    public void saisirNote(int etudiantId, int matiereId, int classeId, String type, int trimestre, double valeur) {

        verifierNonVerrouille(classeId, trimestre);

        Matiere matiere = matiereRepository.findByClasseId(classeId).stream()
            .filter(m -> m.getId() == matiereId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Matière introuvable."));

        if (valeur < matiere.getBaremeMin() || valeur > matiere.getBaremeMax()) {
            throw new IllegalArgumentException(
                "La note doit être entre " + matiere.getBaremeMin() + " et " + matiere.getBaremeMax() + "."
            );
        }

        Note note = new Note(etudiantId, matiereId, type, valeur, trimestre);
        note.setCreePar(SessionManager.getUtilisateurConnecte() != null
            ? SessionManager.getUtilisateurConnecte().getNomUtilisateur() : "inconnu");

        noteRepository.upsert(note);
    }

    /**
     * Efface une saisie directe pour repasser en mode détaillé.
     */
    public void effacerMoyenneDirecte(int etudiantId, int matiereId, int classeId, int trimestre) {
        verifierNonVerrouille(classeId, trimestre);
        noteRepository.supprimer(etudiantId, matiereId, Note.TYPE_MOYENNE_DIRECTE, trimestre);
    }

    /**
     * Récupère les évaluations de CHAQUE matière d'une classe,
     * pour un étudiant et un trimestre donnés.
     */
    public List<NotesMatiere> recupererNotesParMatiere(int etudiantId, int classeId, int trimestre) {

        List<Matiere> matieres = matiereRepository.findByClasseId(classeId);
        List<Note> toutesLesNotes = noteRepository.findByEtudiantAndTrimestre(etudiantId, trimestre);

        return matieres.stream().map(matiere -> {
            NotesMatiere nm = new NotesMatiere(matiere.getId(), matiere.getNom(), matiere.getCoefficient());

            toutesLesNotes.stream()
                .filter(n -> n.getMatiereId() == matiere.getId())
                .forEach(n -> {
                    switch (n.getType()) {
                        case Note.TYPE_DEVOIR1 -> nm.setDevoir1(n.getValeur());
                        case Note.TYPE_DEVOIR2 -> nm.setDevoir2(n.getValeur());
                        case Note.TYPE_COMPOSITION -> nm.setComposition(n.getValeur());
                        case Note.TYPE_MOYENNE_DIRECTE -> nm.setMoyenneDirecte(n.getValeur());
                    }
                });

            return nm;
        }).toList();
    }

    private void verifierNonVerrouille(int classeId, int trimestre) {
        String statut = calculMoyenneRepository.getStatut(classeId, trimestre);
        if ("calcule_verrouille".equals(statut)) {
            throw new IllegalStateException(
                "Les moyennes de ce trimestre sont verrouillées. " +
                "Déverrouillez-les d'abord pour modifier les notes."
            );
        }
    }
}
