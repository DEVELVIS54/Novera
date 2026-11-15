package com.fasodev.gestionscolaire.ui;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Année scolaire active, observable par l'interface : quand elle change, la fenêtre principale
 * met à jour son en-tête et revient au tableau de bord.
 */
public final class AnneeActive {

    private static final StringProperty ANNEE = new SimpleStringProperty();

    private AnneeActive() {
    }

    /** À écouter pour réagir à un changement d'année. */
    public static ReadOnlyStringProperty propriete() {
        return ANNEE;
    }

    /** Année active (lue en base la première fois). */
    public static String lire() {
        if (ANNEE.get() == null) {
            rafraichir();
        }
        return ANNEE.get();
    }

    /** Relit l'année active en base. */
    public static void rafraichir() {
        ANNEE.set(ConfigurationEcoleRepository.obtenirAnneeActive());
    }

    /** Change l'année active : enregistrée en base, puis annoncée à l'interface. */
    public static void definir(String annee) {
        ConfigurationEcoleRepository.definirAnneeActive(annee);
        ANNEE.set(annee);
    }
}