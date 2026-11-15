# Construit l'application Novera (jar unique, puis dossier portable ou installateur .exe).
#
# Utilisation (depuis n'importe ou) :
#   powershell -ExecutionPolicy Bypass -File .\scripts\construire-exe.ps1                 # dossier portable (sans outil supplementaire)
#   powershell -ExecutionPolicy Bypass -File .\scripts\construire-exe.ps1 -Type exe       # installateur Novera-<version>.exe (exige WiX Toolset 3)
#   ... -SansTests   pour sauter les tests, -Console pour garder une fenetre de console (debogage)
param(
    [ValidateSet("app-image", "exe")] [string]$Type = "app-image",
    [switch]$SansTests,
    [switch]$Console
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$pom = [xml](Get-Content "pom.xml" -Raw)
$version = $pom.project.version
$artifact = $pom.project.artifactId
$jar = "$artifact-$version.jar"

# --- outils necessaires ---
$jpackage = $null
$cmd = Get-Command jpackage -ErrorAction SilentlyContinue
if ($cmd) { $jpackage = $cmd.Source }
if (-not $jpackage -and $env:JAVA_HOME) {
    $candidat = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path $candidat) { $jpackage = $candidat }
}
if (-not $jpackage) { throw "jpackage introuvable. Installez un JDK 21 complet (pas un simple JRE) et verifiez JAVA_HOME ou le PATH." }
if ($Type -eq "exe" -and -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
    throw "L'installateur .exe exige WiX Toolset 3 (light.exe et candle.exe dans le PATH). Sans lui, utilisez le mode par defaut (dossier portable)."
}

# --- 1. jar unique ---
"== 1/3 Compilation et jar unique"
if ($SansTests) { mvn clean package -DskipTests } else { mvn clean package }
if ($LASTEXITCODE -ne 0) { throw "La compilation ou les tests ont echoue : rien n'est empaquete." }
if (-not (Test-Path "target\$jar")) { throw "Jar introuvable : target\$jar" }

# --- 2. dossier d'entree propre (le jar seul) ---
$entree = "target\jpackage-input"
Remove-Item $entree -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $entree | Out-Null
Copy-Item "target\$jar" $entree -Force

# --- 3. jpackage ---
"== 2/3 Empaquetage ($Type)"
$dest = "dist"
Remove-Item $dest -Recurse -Force -ErrorAction SilentlyContinue

$arguments = @(
    "--type", $Type,
    "--name", "Novera",
    "--app-version", $version,
    "--vendor", "FasoDev",
    "--description", "Novera - gestion scolaire",
    "--input", $entree,
    "--main-jar", $jar,
    "--main-class", "com.fasodev.gestionscolaire.Launcher",
    "--dest", $dest,
    # java.net.http : synchronisation ; jdk.localedata : dates et nombres en francais ; java.sql : SQLite
    "--add-modules", "java.sql,java.net.http,java.desktop,java.logging,jdk.localedata,jdk.unsupported",
    "--java-options", "-Dfile.encoding=UTF-8"
)
$icone = "src\main\resources\images\novera.ico"
if (Test-Path $icone) { $arguments += @("--icon", $icone) }
if ($Console) { $arguments += "--win-console" }
if ($Type -eq "exe") { $arguments += @("--win-menu", "--win-shortcut", "--win-per-user-install", "--win-dir-chooser") }

& $jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage a echoue." }

"== 3/3 Termine"
if ($Type -eq "app-image") {
    "Dossier portable : $((Resolve-Path "$dest\Novera").Path)"
    "Lancez : $((Resolve-Path "$dest\Novera\Novera.exe").Path)"
    "Pour le distribuer : copiez tout le dossier Novera (pas seulement le .exe)."
} else {
    Get-ChildItem $dest -Filter *.exe | ForEach-Object { "Installateur : $($_.FullName)  ($([math]::Round($_.Length / 1MB, 1)) Mo)" }
}