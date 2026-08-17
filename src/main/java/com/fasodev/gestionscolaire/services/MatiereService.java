package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Matiere;
import com.fasodev.gestionscolaire.repositories.MatiereRepository;

import java.util.List;

public class MatiereService {

    private final MatiereRepository matiereRepository = new MatiereRepository();

    public List<Matiere> listerToutes() {
        return matiereRepository.findAll();
    }

    public List<Matiere> listerParClasse(int classeId) {
        return matiereRepository.findByClasseId(classeId);
    }

    public Matiere creer(Matiere matiere) {
        valider(matiere, null);
        return matiereRepository.save(matiere);
    }

    public void modifier(Matiere matiere) {
        valider(matiere, matiere.getId());
        matiereRepository.update(matiere);
    }

    public void supprimer(int matiereId) {
        if (matiereRepository.aDesNotes(matiereId)) {
            throw new IllegalStateException(
                "Impossible de supprimer cette matière : des notes y sont déjà " +
                "rattachées."
            );
        }
        matiereRepository.delete(matiereId);
    }

    private void valider(Matiere matiere, Integer idActuel) {

        if (matiere.getNom() == null || matiere.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom de la matière est obligatoire.");
        }
        if (matiere.getClasseId() <= 0) {
            throw new IllegalArgumentException("Veuillez sélectionner une classe.");
        }
        if (matiere.getCoefficient() <= 0) {
            throw new IllegalArgumentException("Le coefficient doit être supérieur à 0.");
        }
        if (matiere.getBaremeMin() >= matiere.getBaremeMax()) {
            throw new IllegalArgumentException(
                "Le barème minimum doit être inférieur au barème maximum."
            );
        }

        if (matiereRepository.existeNomDansClasse(
                matiere.getNom().trim(), matiere.getClasseId(), idActuel)) {
            throw new IllegalArgumentException(
                "Une matière \"" + matiere.getNom() + "\" existe déjà dans cette classe."
            );
        }

        matiere.setNom(matiere.getNom().trim());
    }
}
