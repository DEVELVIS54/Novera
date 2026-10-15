package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.models.NotesMatiere;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoyenneCalculatorServiceTest {

    private NotesMatiere directe(String nom, double coefficient, double moyenne) {
        NotesMatiere m = new NotesMatiere(1, nom, coefficient);
        m.setMoyenneDirecte(moyenne);
        return m;
    }

    @Test
    void moyenne_ponderee_par_les_coefficients() {
        // (12 x 3 + 8 x 1) / 4 = 11
        double moyenne = MoyenneCalculatorService.calculerMoyenneTrimestre(
            List.of(directe("Maths", 3, 12), directe("Dessin", 1, 8)));
        assertEquals(11.0, moyenne, 1e-9);
    }

    @Test
    void liste_vide_donne_zero() {
        assertEquals(0.0, MoyenneCalculatorService.calculerMoyenneTrimestre(List.of()), 1e-9);
    }

    @Test
    void coefficients_nuls_donnent_zero_sans_division_par_zero() {
        double moyenne = MoyenneCalculatorService.calculerMoyenneTrimestre(List.of(directe("X", 0, 15)));
        assertEquals(0.0, moyenne, 1e-9);
    }

    @Test
    void appreciations_aux_bornes() {
        assertEquals("Très bien", MoyenneCalculatorService.appreciation(16));
        assertEquals("Bien", MoyenneCalculatorService.appreciation(15.99));
        assertEquals("Assez bien", MoyenneCalculatorService.appreciation(12));
        assertEquals("Passable", MoyenneCalculatorService.appreciation(10));
        assertEquals("Insuffisant", MoyenneCalculatorService.appreciation(9.99));
    }
}
