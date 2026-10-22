package com.fasodev.gestionscolaire.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotesMatiereTest {

    private NotesMatiere matiere() {
        return new NotesMatiere(1, "Maths", 3);
    }

    @Test
    void formuleDetaillee_composition_et_moyenne_des_devoirs() {
        NotesMatiere m = matiere();
        m.setDevoir1(10.0);
        m.setDevoir2(14.0);       // MD = 12
        m.setComposition(16.0);   // (16 + 12) / 2 = 14
        assertEquals(14.0, m.calculerMoyenneMatiere(), 1e-9);
        assertTrue(m.estComplete());
    }

    @Test
    void moyenneDirecte_est_prioritaire() {
        NotesMatiere m = matiere();
        m.setDevoir1(4.0);
        m.setDevoir2(4.0);
        m.setComposition(4.0);
        m.setMoyenneDirecte(15.5);
        assertEquals(15.5, m.calculerMoyenneMatiere(), 1e-9);
        assertTrue(m.estSaisieDirecte());
    }

    @Test
    void sansAucuneNote_la_moyenne_est_zero() {
        assertEquals(0.0, matiere().calculerMoyenneMatiere(), 1e-9);
        assertFalse(matiere().estComplete());
    }

    @Test
    void sansComposition_seule_la_moyenne_des_devoirs_compte() {
        NotesMatiere m = matiere();
        m.setDevoir1(8.0);
        m.setDevoir2(12.0);
        assertEquals(10.0, m.calculerMoyenneMatiere(), 1e-9);
        assertFalse(m.estComplete());
    }

    @Test
    void unSeulDevoir_est_utilise_tel_quel() {
        NotesMatiere m = matiere();
        m.setDevoir2(13.0);
        assertEquals(13.0, m.calculerMoyenneMatiere(), 1e-9);
    }
}
