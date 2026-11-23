package com.fasodev.gestionscolaire.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** L'ouverture automatique ne doit jamais faire échouer la génération d'un document. */
class OuvrirFichierTest {

    @Test
    void un_fichier_nul_est_ignore() {
        assertDoesNotThrow(() -> OuvrirFichier.ouvrir(null));
    }

    @Test
    void un_fichier_absent_est_ignore(@TempDir Path dossier) {
        File absent = dossier.resolve("inexistant.pdf").toFile();

        assertDoesNotThrow(() -> OuvrirFichier.ouvrir(absent));
    }
}