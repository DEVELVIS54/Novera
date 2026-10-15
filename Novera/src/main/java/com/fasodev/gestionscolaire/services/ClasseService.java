package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.Classe;
import com.fasodev.gestionscolaire.repositories.ClasseRepository;

import java.util.List;

public class ClasseService {

    private final ClasseRepository classeRepository = new ClasseRepository();

    public List<Classe> listerToutes() {
        return classeRepository.findAll();
    }

    public Classe creer(Classe classe) {
        valider(classe, null);
        Classe saved = classeRepository.save(classe);

        // TODO (prochaine étape) : brancher ici sauvegarderEtSynchroniser()
        // une fois le SyncService implémenté — voir doc SYNC_PERMANENTE.md

        return saved;
    }

    public void modifier(Classe classe) {
        valider(classe, classe.getId());
        classeRepository.update(classe);
    }

    public void supprimer(int classeId) {
        if (classeRepository.aDesEtudiants(classeId)) {
            throw new IllegalStateException(
                "Impossible de supprimer cette classe : des étudiants y sont " +
                "encore rattachés. Déplacez-les ou marquez-les partis d'abord."
            );
        }
        classeRepository.delete(classeId);
    }

    private void valider(Classe classe, Integer idActuel) {

        if (classe.getNom() == null || classe.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom de la classe est obligatoire.");
        }

        if (classeRepository.existeNom(classe.getNom().trim(), idActuel)) {
            throw new IllegalArgumentException(
                "Une classe nommée \"" + classe.getNom() + "\" existe déjà."
            );
        }

        if (classe.isEstClasseExamen() &&
            (classe.getNomExamen() == null || classe.getNomExamen().isBlank())) {
            throw new IllegalArgumentException(
                "Le nom de l'examen est requis pour une classe d'examen national."
            );
        }

        classe.setNom(classe.getNom().trim());
    }
}
