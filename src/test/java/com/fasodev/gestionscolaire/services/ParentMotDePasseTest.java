package com.fasodev.gestionscolaire.services;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie le format et la variété des mots de passe générés pour les parents. */
class ParentMotDePasseTest {

    /** Caractères autorisés : pas de 0, O, 1, I, L (confusions à l'impression). */
    private static final String AUTORISES = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    @Test
    void le_mot_de_passe_a_6_caracteres_lisibles() {
        for (int i = 0; i < 1000; i++) {
            String mdp = ParentIdentifiantService.genererMotDePasse();

            assertEquals(6, mdp.length());
            for (char c : mdp.toCharArray()) {
                assertTrue(AUTORISES.indexOf(c) >= 0, "caractère interdit : " + c);
            }
        }
    }

    @Test
    void les_mots_de_passe_sont_varies() {
        Set<String> generes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generes.add(ParentIdentifiantService.genererMotDePasse());
        }

        // 31^6 combinaisons possibles : sur 1000 tirages, on attend (presque) tous différents.
        assertTrue(generes.size() > 990, "trop de doublons : " + generes.size());
    }
}