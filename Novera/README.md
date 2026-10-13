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

## Données locales

La base se trouve dans le dossier de l'application (`%LOCALAPPDATA%\GestionScolaire\gestion_scolaire.db`
sous Windows). Le nom de dossier `GestionScolaire` est conservé volontairement pour ne pas perdre
les bases existantes. Les reçus, fiches parents et bulletins PDF sont rangés dans ses sous-dossiers.

## Modules

| Module | Accès |
|---|---|
| Tableau de bord, Recherche | Tous |
| Étudiants, Classes, Matières, Notes | Tous |
| Calcul des moyennes (verrouillage) et **bulletins PDF** | Tous |
| Passage en classe, Examen national, Réinscriptions | Tous |
| Frais de scolarité, Paiements (reçus PDF), Identifiants parents | Tous |
| **Utilisateurs** (créer un Secrétaire, activer/désactiver, mot de passe) | Directeur |
| **Synchronisation** (adresse du serveur, clé, envoi manuel) | Directeur |

## Synchronisation

Des triggers SQLite notent chaque ajout, modification ou suppression dans `sync_queue`.
Un service d'arrière-plan (toutes les 60 s, ou bouton « Synchroniser maintenant ») envoie ces
opérations par lots à `POST /api/sync/batch` du serveur (voir `../novera-server`).
Les comptes utilisateurs ne sont **jamais** envoyés, ni la clé d'accès, l'adresse du serveur
et l'identifiant du poste.

Limite connue : le serveur identifie une ligne par (table, id). Il convient donc à **un seul poste
desktop par école**. Plusieurs postes en parallèle demanderaient des identifiants uniques (UUID).

## Structure

```
src/main/java/com/fasodev/gestionscolaire/
├── Main.java
├── database/      AppPaths, DatabaseConnection, DatabaseInitializer (schéma, migrations, triggers)
├── models/        entités
├── repositories/  accès SQLite (dont SyncRepository)
├── services/      logique métier (moyennes, passage, examens, paiements, sync, bulletins…)
└── ui/            DashboardView + controllers/ (JavaFX, FXML dans src/main/resources/views)
src/test/java/     tests JUnit 5
```

## À savoir

- Les moyennes trimestrielles sont uniques par (élève, trimestre) : le schéma actuel ne conserve pas
  l'historique de plusieurs années scolaires pour un même élève. À traiter avant la 2e année d'utilisation.
- Le passage de classe repose sur le champ « niveau » (6eme, 5eme … terminale) ; une classe marquée
  « fin de parcours » ne passe pas à la suivante.
