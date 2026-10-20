package com.fasodev.gestionscolaire.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassageStatutTest {

    // Seuils par défaut de l'application : admission 10, redoublement 8.

    @Test
    void admis_des_le_seuil_d_admission() {
        assertEquals("ADMIS", PassageClasseService.determinerStatut(10.0, 10.0, 8.0));
        assertEquals("ADMIS", PassageClasseService.determinerStatut(14.2, 10.0, 8.0));
    }

    @Test
    void redouble_sous_le_seuil_de_redoublement() {
        assertEquals("REDOUBLE", PassageClasseService.determinerStatut(7.99, 10.0, 8.0));
        assertEquals("REDOUBLE", PassageClasseService.determinerStatut(0, 10.0, 8.0));
    }

    @Test
    void deliberation_entre_les_deux_seuils() {
        assertEquals("DELIBERATION", PassageClasseService.determinerStatut(8.0, 10.0, 8.0));
        assertEquals("DELIBERATION", PassageClasseService.determinerStatut(9.99, 10.0, 8.0));
    }

    @Test
    void les_seuils_configures_sont_respectes() {
        assertEquals("ADMIS", PassageClasseService.determinerStatut(9.0, 9.0, 7.0));
        assertEquals("REDOUBLE", PassageClasseService.determinerStatut(6.5, 9.0, 7.0));
    }
}
