# Novera — Application desktop (JavaFX)

Logiciel de gestion scolaire. L'application fonctionne **toujours en local** (SQLite) et envoie
ses modifications au serveur Novera dès qu'il est joignable.

## Lancer

```bash
mvn clean javafx:run        # lancer l'application
mvn test                    # lancer les tests
mvn clean package           # jar unique (Shade) dans target/
```

Prérequis : JDK 21 et Maven. Au premier lancement, un assistant crée le compte **Directeur** (ADMIN).

## Construire le .exe

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\construire-exe.ps1            # dossier portable : dist\Novera\Novera.exe
powershell -ExecutionPolicy Bypass -File .\scripts\construire-exe.ps1 -Type exe  # installateur (exige WiX Toolset 3)
```

Le script compile, lance les tests, puis appelle `jpackage` (fourni avec le JDK 21) : Java est embarqué,
l'école n'a rien à installer. Options : `-SansTests`, `-Console` (fenêtre de console pour le débogage).
Un fichier `src/main/resources/images/novera.ico`, s'il existe, devient l'icône de l'application.

Le jar démarre sur `Launcher` et non sur `Main` : une classe qui hérite directement de
`javafx.application.Application` échoue hors de Maven avec « JavaFX runtime components are missing ».

## Mot de passe oublié

Fermez Novera, puis dans le dossier du projet :

```
mvn compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.mainClass=com.fasodev.gestionscolaire.tools.ResetMotDePasse"
```

Ou double-cliquez sur `reinitialiser_mot_de_passe.bat` (à la racine du projet).

L'outil (`tools/ResetMotDePasse.java`) sauvegarde la base, liste les comptes et remplace le mot de passe
du compte choisi. Aucune donnée n'est supprimée. Sous PowerShell, gardez les guillemets autour de `-D...`.

## Données locales

La base se trouve dans le dossier de l'application (`%LOCALAPPDATA%\GestionScolaire\gestion_scolaire.db`
sous Windows). Le nom de dossier `GestionScolaire` est conservé volontairement pour ne pas perdre
les bases existantes. Les reçus, fiches parents et bulletins PDF sont rangés dans ses sous-dossiers.
La base n'est jamais dans le dossier d'installation : une mise à jour du logiciel ne touche pas aux données.

## Navigation

- **Dock** en bas de la fenêtre : 5 familles (Accueil, Élèves, Évaluations, Finances, Réglages). Un clic
  ouvre les pages de la famille ; la famille de la page ouverte reste en évidence. « Réglages »
  (Utilisateurs, Année scolaire, Synchronisation) n'apparaît que pour le Directeur, avec une pastille
  quand des modifications attendent d'être envoyées au serveur.
- **Palette de commandes** : `Ctrl+K` (ou clic sur la barre de recherche) pour aller à une page ou
  retrouver un élève en tapant quelques lettres. Flèches, Entrée, Échap.
- La **pastille « Année 2026-2027 »** de la barre du haut indique l'année scolaire active.
- Le **thème** (couleurs, verre, dock) est dans `src/main/resources/css/novera-theme.css`.

## Modules

| Module | Accès |
|---|---|
| Tableau de bord, Recherche | Tous |
| Étudiants, Classes, Matières, Notes | Tous |
| Calcul des moyennes (verrouillage) et **bulletins PDF** | Tous |
| Passage en classe, Examen national, Réinscriptions | Tous |
| Frais de scolarité, Paiements (reçus PDF), Identifiants parents | Tous |
| **Utilisateurs** (créer un Secrétaire, activer/désactiver, mot de passe) | Directeur |
| **Année scolaire** (consulter une année passée, ouvrir la suivante) | Directeur |
| **Synchronisation** (adresse du serveur, clé, envoi manuel) | Directeur |

## Année scolaire

L'application travaille sur **une année scolaire active** (format `2026-2027`, qui commence en septembre).
Notes, calculs de moyennes, moyennes trimestrielles, frais de scolarité et paiements sont rangés par année :
les écrans n'affichent et n'enregistrent que l'année active, et les données des autres années sont conservées.

- Réglages → **Année scolaire** : choisir une année existante (pour consulter d'anciens résultats) ou
  **ouvrir l'année suivante**. L'ouverture recopie les frais de scolarité de l'année précédente (modifiables).
- Passage en classe, Examen national et Réinscriptions proposent l'année active par défaut.
- L'année active est enregistrée dans la configuration de l'école et synchronisée : l'espace parents
  n'affiche que cette année.

## Base de données et migrations

Le schéma est versionné : la table `schema_version` liste les migrations appliquées.
Les migrations sont dans `database/SchemaMigrations.java` (liste `MIGRATIONS`), appliquées au démarrage,
dans l'ordre, une seule fois, chacune dans une transaction.

- Pour changer le schéma : ajouter une migration avec le **numéro suivant**. Ne jamais modifier ni
  renuméroter une migration déjà livrée.
- Une base plus récente que l'application est refusée (on n'ouvre pas une base migrée avec une vieille version).
- Avant d'essayer une migration sur de vraies données, copier `gestion_scolaire.db`.
- Les tests d'intégration créent une vraie base dans un fichier temporaire
  (`DatabaseConnection.utiliserBasePourTests`) : la base de l'utilisateur n'est jamais touchée.

## Synchronisation

Des triggers SQLite notent chaque ajout, modification ou suppression dans `sync_queue`.
Un service d'arrière-plan (toutes les 60 s, ou bouton « Synchroniser maintenant ») envoie ces
opérations par lots à `POST /api/sync/batch` du serveur (voir `novera-server`).
Les comptes utilisateurs ne sont **jamais** envoyés, ni la clé d'accès, l'adresse du serveur
et l'identifiant du poste.

Limite connue : le serveur identifie une ligne par (table, id). Il convient donc à **un seul poste
desktop par école**. Plusieurs postes en parallèle demanderaient des identifiants uniques (UUID).

## Structure

```
src/main/java/com/fasodev/gestionscolaire/
├── Launcher.java  point d'entrée du jar / du .exe (lance Main)
├── Main.java
├── database/      AppPaths, DatabaseConnection, DatabaseInitializer (schéma de base, triggers),
│                  SchemaMigrations + Migration*.java (migrations numérotées)
├── models/        entités
├── repositories/  accès SQLite (dont SyncRepository)
├── services/      logique métier (moyennes, passage, examens, paiements, sync, bulletins…)
├── ui/            AnneeActive, DashboardView + controllers/ (JavaFX, FXML dans src/main/resources/views)
└── utils/         AnneeScolaire (calculs sur « 2026-2027 »)
src/test/java/     tests JUnit 5 (dont tests d'intégration sur base temporaire)
scripts/           construire-exe.ps1
```

## À savoir

- Le passage de classe repose sur le champ « niveau » (6eme, 5eme … terminale) ; une classe marquée
  « fin de parcours » ne passe pas à la suivante.
- La subvention de l'État réduit les frais d'un élève affecté selon son palier (CEP ou BEPC) tant que
  la subvention est active ; la perte automatique après un nombre de redoublements n'est pas encore codée.
- Le `.exe` n'est pas signé numériquement : Windows peut afficher un avertissement SmartScreen au premier lancement.
