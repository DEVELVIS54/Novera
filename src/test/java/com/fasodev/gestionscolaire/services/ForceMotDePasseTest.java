package com.fasodev.gestionscolaire.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForceMotDePasseTest {

    @Test
    void vide_ou_null_donne_aucune_force() {
        assertEquals(ForceMotDePasse.AUCUNE, ForceMotDePasse.evaluer(""));
        assertEquals(ForceMotDePasse.AUCUNE, ForceMotDePasse.evaluer(null));
    }

    @Test
    void mot_de_passe_court_ou_simple_est_faible() {
        assertEquals(ForceMotDePasse.FAIBLE, ForceMotDePasse.evaluer("abc"));
        assertEquals(ForceMotDePasse.FAIBLE, ForceMotDePasse.evaluer("azertyuiop"));   // 1 seul type
        assertEquals(ForceMotDePasse.FAIBLE, ForceMotDePasse.evaluer("Ab1"));
    }

    @Test
    void huit_caracteres_et_deux_types_donne_moyenne() {
        assertEquals(ForceMotDePasse.MOYENNE, ForceMotDePasse.evaluer("abcdef12"));
        assertEquals(ForceMotDePasse.MOYENNE, ForceMotDePasse.evaluer("Abcdefgh"));
    }

    @Test
    void dix_caracteres_et_trois_types_donne_forte() {
        assertEquals(ForceMotDePasse.FORTE, ForceMotDePasse.evaluer("Abcdef1234"));
        assertEquals(ForceMotDePasse.FORTE, ForceMotDePasse.evaluer("mot-de-passe-2026"));
    }

    @Test
    void dix_caracteres_mais_deux_types_reste_moyenne() {
        assertEquals(ForceMotDePasse.MOYENNE, ForceMotDePasse.evaluer("abcdefg123"));
    }

    @Test
    void libelles() {
        assertEquals("Faible", ForceMotDePasse.libelle(ForceMotDePasse.FAIBLE));
        assertEquals("Moyen", ForceMotDePasse.libelle(ForceMotDePasse.MOYENNE));
        assertEquals("Fort", ForceMotDePasse.libelle(ForceMotDePasse.FORTE));
        assertEquals("", ForceMotDePasse.libelle(ForceMotDePasse.AUCUNE));
    }
}