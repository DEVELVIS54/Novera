# Lot 10 - perte automatique de la subvention apres N redoublements (N configurable)
# Script 100 % ASCII (accents ecrits ~uXXXX~). A lancer depuis la racine du projet :
#   powershell -ExecutionPolicy Bypass -File .\appliquer_lot10.ps1
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
function Patch($chemin, $ancien, $nouveau, $marqueur) {
    $p = Join-Path (Get-Location).Path $chemin
    $brut = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    $crlf = $brut.Contains("`r`n")
    $t = $brut.Replace("`r`n", "`n")
    $ancien = (Tok $ancien).Replace("`r`n", "`n")
    $nouveau = (Tok $nouveau).Replace("`r`n", "`n")
    if ($marqueur -and $t.Contains($marqueur)) { "  deja ok : $(Split-Path $chemin -Leaf)"; return }
    $n = ([regex]::Matches($t, [regex]::Escape($ancien))).Count
    if ($n -ne 1) { throw "Patch impossible dans $chemin : motif trouve $n fois (attendu 1). Debut : $($ancien.Substring(0, [Math]::Min(80, $ancien.Length)))" }
    $t = $t.Replace($ancien, $nouveau)
    if ($crlf) { $t = $t.Replace("`n", "`r`n") }
    [System.IO.File]::WriteAllText($p, $t, $utf8)
    "  modifie : $(Split-Path $chemin -Leaf)"
}

$main = "src\main\java\com\fasodev\gestionscolaire"
$test = "src\test\java\com\fasodev\gestionscolaire"
$sub = "$main\services\SubventionEtatService.java"
$pas = "$main\services\PassageClasseService.java"
$exa = "$main\services\ExamenNationalService.java"

foreach ($f in @($sub, $pas, $exa)) { if (-not (Test-Path $f)) { throw "Fichier introuvable : $f" } }
if (-not (Select-String -Path "$main\database\DatabaseConnection.java" -SimpleMatch "utiliserBasePourTests" -Quiet)) { throw "Lot 2 non applique (base redirigeable absente)." }
"Prerequis presents. Application du lot 10..."

# ---------- 1. SubventionEtatService : la regle ----------
$p = Join-Path (Get-Location).Path $sub
$brut = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
if ($brut.Contains("appliquerPerteSiSeuilAtteint")) { "  deja ok : SubventionEtatService.java" }
else {
    $crlf = $brut.Contains("`r`n")
    $t = $brut.Replace("`r`n", "`n")
    $methode = Tok @'

    /**
     * Perte de la subvention de l'~u00C9~tat : un ~u00E9~l~u00E8~ve affect~u00E9~ dont la subvention est active la perd quand
     * son nombre de redoublements atteint le seuil configur~u00E9~. Le changement est fait sur l'objet ;
     * l'appelant l'enregistre (EtudiantRepository.mettreAJour).
     *
     * @return vrai si la subvention vient d'~u00EA~tre perdue
     */
    public boolean appliquerPerteSiSeuilAtteint(Etudiant etudiant) {
        if (!etudiant.isAffecteEtat() || !etudiant.isSubventionActive()) {
            return false;
        }
        if (etudiant.getNombreRedoublements() >= getConfiguration().seuilRedoublementsPerte) {
            etudiant.setSubventionActive(false);
            return true;
        }
        return false;
    }
'@
    if ([regex]::Matches($t, '\}\s*$').Count -ne 1) { throw "SubventionEtatService.java : fin de classe introuvable." }
    $t = [regex]::Replace($t, '\}\s*$', ($methode + "`n}`n"))
    if ($crlf) { $t = $t.Replace("`n", "`r`n") }
    [System.IO.File]::WriteAllText($p, $t, $utf8)
    "  modifie : SubventionEtatService.java"
}

# ---------- 2. Les deux endroits ou un redoublement est compte ----------
Patch $pas @'
etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
'@ @'
etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
            new SubventionEtatService().appliquerPerteSiSeuilAtteint(etudiant);
'@ 'appliquerPerteSiSeuilAtteint'

Patch $exa @'
etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
'@ @'
etudiant.setNombreRedoublements(etudiant.getNombreRedoublements() + 1);
                new SubventionEtatService().appliquerPerteSiSeuilAtteint(etudiant);
'@ 'appliquerPerteSiSeuilAtteint'

# ---------- 3. Tests ----------
Ecrire "$test\services\SubventionPerteIntegrationTest.java" @'
package com.fasodev.gestionscolaire.services;

import com.fasodev.gestionscolaire.database.DatabaseConnection;
import com.fasodev.gestionscolaire.database.DatabaseInitializer;
import com.fasodev.gestionscolaire.models.Etudiant;
import com.fasodev.gestionscolaire.models.ResultatPassage;
import com.fasodev.gestionscolaire.repositories.ConfigurationEcoleRepository;
import com.fasodev.gestionscolaire.repositories.EtudiantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perte automatique de la subvention de l'Etat : un eleve affecte la perd quand son nombre de
 * redoublements atteint le seuil configure. Eleve 1 : affecte (palier CEP). Eleve 2 : non affecte.
 */
class SubventionPerteIntegrationTest {

    @TempDir
    Path dossier;

    private SubventionEtatService subvention;
    private String annee;

    @BeforeEach
    void preparer() throws SQLException {
        DatabaseConnection.utiliserBasePourTests(dossier.resolve("test.db").toString());
        DatabaseInitializer.initialiser();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('6eme A', '6eme')");
            st.execute("INSERT INTO classes (nom, niveau) VALUES ('5eme A', '5eme')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id, affecte_etat, palier_subvention) "
                + "VALUES ('Awa', 'Traore', 1, 1, 'CEP')");
            st.execute("INSERT INTO etudiants (prenom, nom, classe_id) VALUES ('Issa', 'Ouedraogo', 1)");
        }

        subvention = new SubventionEtatService();
        subvention.mettreAJourConfiguration(50, 30, 2);   // perte apres 2 redoublements
        annee = ConfigurationEcoleRepository.obtenirAnneeActive();
    }

    @AfterEach
    void nettoyer() {
        DatabaseConnection.reinitialiserPourTests();
    }

    private String texte(String sql) throws SQLException {
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void redoubler(int etudiantId) {
        ResultatPassage r = new ResultatPassage(etudiantId, "Eleve " + etudiantId, "6eme A", annee);
        r.setStatutFinal("REDOUBLE");
        PassageClasseService.appliquerPassage(r);
    }

    @Test
    void la_subvention_est_perdue_au_seuil_de_redoublements() throws SQLException {
        redoubler(1);
        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 1 redoublement : conservee

        redoubler(1);
        assertEquals("2", texte("SELECT nombre_redoublements FROM etudiants WHERE id = 1"));
        assertEquals("0", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 2 redoublements : perdue
    }

    @Test
    void un_eleve_non_affecte_n_est_pas_concerne() throws SQLException {
        redoubler(2);
        redoubler(2);

        assertEquals("2", texte("SELECT nombre_redoublements FROM etudiants WHERE id = 2"));
        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 2"));
    }

    @Test
    void le_seuil_configure_est_respecte() throws SQLException {
        subvention.mettreAJourConfiguration(50, 30, 3);

        redoubler(1);
        redoubler(1);

        assertEquals("1", texte("SELECT subvention_active FROM etudiants WHERE id = 1"));   // 2 < 3
    }

    @Test
    void la_perte_n_est_signalee_qu_une_seule_fois() {
        Etudiant eleve = EtudiantRepository.obtenirParId(1);
        eleve.setNombreRedoublements(2);

        assertTrue(subvention.appliquerPerteSiSeuilAtteint(eleve));
        assertFalse(eleve.isSubventionActive());
        assertFalse(subvention.appliquerPerteSiSeuilAtteint(eleve));   // deja perdue : rien a faire
    }

    @Test
    void une_fois_perdue_la_reduction_est_nulle() {
        redoubler(1);
        redoubler(1);

        Etudiant eleve = EtudiantRepository.obtenirParId(1);

        assertEquals(0.0, subvention.getPourcentageReduction(eleve));
        assertEquals(100000.0, subvention.calculerFraisReels(eleve, 100000), 0.001);
    }
}
'@

"----- verification (tout doit etre True)"
"{0,-6} regle dans SubventionEtatService" -f (Select-String -Path $sub -SimpleMatch "appliquerPerteSiSeuilAtteint" -Quiet)
"{0,-6} appelee par PassageClasseService" -f (Select-String -Path $pas -SimpleMatch "appliquerPerteSiSeuilAtteint" -Quiet)
"{0,-6} appelee par ExamenNationalService" -f (Select-String -Path $exa -SimpleMatch "appliquerPerteSiSeuilAtteint" -Quiet)
"{0,-6} test" -f (Test-Path "$test\services\SubventionPerteIntegrationTest.java")

"----- mvn test (100 attendus si le lot 9 est applique, 81 sinon)"
mvn test 2>&1 | Select-String "Tests run:.*Fail|BUILD|FAILURE|ERROR\]   " | Select-Object -Last 14
git --no-pager status -sb