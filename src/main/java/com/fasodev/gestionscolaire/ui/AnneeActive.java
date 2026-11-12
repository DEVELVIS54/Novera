package com.fasodev.gestionscolaire.ui;

import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * AnnÃ©e scolaire active, observable par l'interface : quand elle change, la fenÃªtre principale
 * met Ã  jour son en-tÃªte et revient au tableau de bord.
 */
public final class AnneeActive {

    private static final StringProperty ANNEE = new SimpleStringProperty();

    private AnneeActive() {
    }

    /** Ã€ Ã©couter pour rÃ©agir Ã  un changement d'annÃ©e. */
    public static ReadOnlyStringProperty propriete() {
        return ANNEE;
    }

    /** AnnÃ©e active (lue en base la premiÃ¨re fois). */
    public static String lire() {
        if (ANNEE.get() == null) {
            rafraichir();
        }
        return ANNEE.get();
    }

    /** Relit l'annÃ©e active en base. */
    public static void rafraichir() {
        ANNEE.set(ConfigurationEcoleRepository.obtenirAnneeActive());
    }

    /** Change l'annÃ©e active : enregistrÃ©e en base, puis annoncÃ©e Ã  l'interface. */
    public static void definir(String annee) {
        ConfigurationEcoleRepository.definirAnneeActive(annee);
        ANNEE.set(annee);
    }
}