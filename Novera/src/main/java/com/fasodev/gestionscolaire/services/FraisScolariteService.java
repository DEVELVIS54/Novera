package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.FraisScolarite;
import com.fasodev.gestionscolaire.repositories.FraisScolariteRepository;

import java.util.List;

public class FraisScolariteService {

    private final FraisScolariteRepository fraisRepository = new FraisScolariteRepository();

    public List<FraisScolarite> listerTous() {
        return fraisRepository.findAll();
    }

    public double getMontantTotalParClasse(int classeId) {
        return fraisRepository.getMontantTotalParClasse(classeId);
    }

    public FraisScolarite creer(FraisScolarite frais) {
        valider(frais);
        return fraisRepository.save(frais);
    }

    public void modifier(FraisScolarite frais) {
        valider(frais);
        fraisRepository.update(frais);
    }

    public void supprimer(int id) {
        fraisRepository.delete(id);
    }

    private void valider(FraisScolarite frais) {
        if (frais.getClasseId() <= 0) {
            throw new IllegalArgumentException("Veuillez sélectionner une classe.");
        }
        if (frais.getMontant() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0.");
        }
    }
}
