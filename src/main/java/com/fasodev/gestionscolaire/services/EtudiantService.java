package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;

import java.time.LocalDate;
import java.util.List;

public class EtudiantService {

    private final EtudiantRepository etudiantRepository = new EtudiantRepository();

    public List<Etudiant> listerTous() {
        return etudiantRepository.findAll();
    }

    public List<Etudiant> listerActifs() {
        return etudiantRepository.findActifs();
    }

    public List<Etudiant> listerParClasse(int classeId) {
        return etudiantRepository.findByClasseId(classeId);
    }

    public Etudiant creer(Etudiant etudiant) {
        valider(etudiant, null);
        return etudiantRepository.save(etudiant);
    }

    public void modifier(Etudiant etudiant) {
        valider(etudiant, etudiant.getId());
        etudiantRepository.update(etudiant);
    }

    public void marquerCommeParti(int etudiantId, LocalDate dateDepart, String raison) {
        if (dateDepart == null) {
            throw new IllegalArgumentException("La date de départ est obligatoire.");
        }
        etudiantRepository.marquerCommeParti(etudiantId, dateDepart, raison);
    }

    private void valider(Etudiant etudiant, Integer idActuel) {

        if (etudiant.getPrenom() == null || etudiant.getPrenom().isBlank()) {
            throw new IllegalArgumentException("Le prénom est obligatoire.");
        }
        if (etudiant.getNom() == null || etudiant.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire.");
        }
        if (etudiant.getClasseId() <= 0) {
            throw new IllegalArgumentException("Veuillez sélectionner une classe.");
        }

        if (etudiant.getMatricule() != null && !etudiant.getMatricule().isBlank()
                && etudiantRepository.existeMatricule(etudiant.getMatricule().trim(), idActuel)) {
            throw new IllegalArgumentException(
                "Le matricule \"" + etudiant.getMatricule() + "\" est déjà utilisé."
            );
        }

        if (etudiant.isAffecteEtat() && etudiant.getPalierSubvention() == null) {
            throw new IllegalArgumentException(
                "Veuillez préciser le palier de subvention (CEP ou BEPC)."
            );
        }

        etudiant.setPrenom(etudiant.getPrenom().trim());
        etudiant.setNom(etudiant.getNom().trim());
    }
}
