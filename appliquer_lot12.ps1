# Lot 12 - les PDF generes (bulletins de classe, fiches d'identifiants) s'ouvrent automatiquement
# Script 100 % ASCII (accents ecrits ~uXXXX~). A lancer depuis la racine du projet :
#   powershell -ExecutionPolicy Bypass -File .\appliquer_lot12.ps1
$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)
if (-not (Test-Path "pom.xml")) { throw "Lancez ce script depuis la racine du projet (pom.xml introuvable)." }

function Tok($s) {
    [regex]::Replace($s, '~u([0-9A-F]{4})~', { param($m) [string][char][Convert]::ToInt32($m.Groups[1].Value, 16) })
}
function Ecrire($chemin, $contenu) {
    New-Item -ItemType Directory -Force -Path (Split-Path $chemin) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $chemin), (Tok $contenu), $utf8)
    "  ecrit   : $(Split-Path $chemin -Leaf)"
}

$main = "src\main\java\com\fasodev\gestionscolaire"
$test = "src\test\java\com\fasodev\gestionscolaire"
$mo = "$main\ui\controllers\MoyennesController.java"
$id = "$main\ui\controllers\IdentifiantsParentsController.java"
foreach ($f in @($mo, $id)) { if (-not (Test-Path $f)) { throw "Fichier introuvable : $f" } }
"Prerequis presents. Application du lot 12..."

# ---------- 1. Classe utilitaire ----------
Ecrire "$main\ui\OuvrirFichier.java" @'
package com.fasodev.gestionscolaire.ui;

import java.awt.Desktop;
import java.io.File;

/**
 * Ouvre un fichier (PDF g~u00E9~n~u00E9~r~u00E9~) avec l'application par d~u00E9~faut du syst~u00E8~me, comme le fait d~u00E9~j~u00E0~
 * l'~u00E9~cran des paiements pour les re~u00E7~us. Ne bloque jamais : si l'ouverture est impossible
 * (pas d'environnement graphique, fichier absent, aucune visionneuse), le fichier reste cr~u00E9~~u00E9~.
 */
public final class OuvrirFichier {

    private OuvrirFichier() {
    }

    /** Ouvre le fichier ; ne fait rien (sans erreur) s'il est nul, absent ou si le syst~u00E8~me ne le permet pas. */
    public static void ouvrir(File fichier) {
        if (fichier == null || !fichier.exists()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(fichier);
            }
        } catch (Exception e) {
            System.out.println("Impossible d'ouvrir automatiquement le fichier : " + e.getMessage());
        }
    }
}
'@

# ---------- 2. Bulletins de classe : ouverture du PDF ----------
$p = Join-Path (Get-Location).Path $mo
$brut = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
if ($brut.Contains("OuvrirFichier.ouvrir(pdf)")) { "  deja ok : MoyennesController.java" }
else {
    $crlf = $brut.Contains("`r`n")
    $t = $brut.Replace("`r`n", "`n")
    $ancien = ".genererBulletinsClasse(classe.getId(), trimestre);"
    if ([regex]::Matches($t, [regex]::Escape($ancien)).Count -ne 1) { throw "MoyennesController.java : appel de generation introuvable." }
    $t = $t.Replace($ancien, $ancien + "`n            com.fasodev.gestionscolaire.ui.OuvrirFichier.ouvrir(pdf);")
    if ($crlf) { $t = $t.Replace("`n", "`r`n") }
    [System.IO.File]::WriteAllText($p, $t, $utf8)
    "  modifie : MoyennesController.java"
}

# ---------- 3. Fiches d'identifiants : ouverture du PDF ----------
$p = Join-Path (Get-Location).Path $id
$brut = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
if ($brut.Contains("OuvrirFichier.ouvrir(fichier)")) { "  deja ok : IdentifiantsParentsController.java" }
else {
    $crlf = $brut.Contains("`r`n")
    $t = $brut.Replace("`r`n", "`n")
    $re = '(?s)(FicheIdentifiantPdfService\.genererPdf\(.*?fichier\.getAbsolutePath\(\)\s*\);)'
    if ([regex]::Matches($t, $re).Count -ne 1) { throw "IdentifiantsParentsController.java : appel de generation introuvable ($([regex]::Matches($t, $re).Count) fois)." }
    $t = [regex]::Replace($t, $re, [System.Text.RegularExpressions.MatchEvaluator]{ param($m) $m.Groups[1].Value + "`n                    com.fasodev.gestionscolaire.ui.OuvrirFichier.ouvrir(fichier);" })
    if ($crlf) { $t = $t.Replace("`n", "`r`n") }
    [System.IO.File]::WriteAllText($p, $t, $utf8)
    "  modifie : IdentifiantsParentsController.java"
}

# ---------- 4. Test ----------
Ecrire "$test\ui\OuvrirFichierTest.java" @'
package com.fasodev.gestionscolaire.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** L'ouverture automatique ne doit jamais faire ~u00E9~chouer la g~u00E9~n~u00E9~ration d'un document. */
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
'@

"----- verification (tout doit etre True)"
"{0,-6} OuvrirFichier.java" -f (Test-Path "$main\ui\OuvrirFichier.java")
"{0,-6} bulletins" -f (Select-String -Path $mo -SimpleMatch "OuvrirFichier.ouvrir(pdf)" -Quiet)
"{0,-6} fiches" -f (Select-String -Path $id -SimpleMatch "OuvrirFichier.ouvrir(fichier)" -Quiet)
"{0,-6} test" -f (Test-Path "$test\ui\OuvrirFichierTest.java")

"----- mvn test (resume)"
mvn test 2>&1 | Select-String "Tests run:.*Fail|BUILD|FAILURE|ERROR\]   " | Select-Object -Last 12
git --no-pager status -sb