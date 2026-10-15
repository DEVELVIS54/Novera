# Gestion Scolaire — Desktop (JavaFX)

## 🚀 Démarrage rapide

### 1. Extraire le projet
Dézippe `GestionScolaire-Desktop.zip` où tu veux sur ton PC 
(ex: `C:\Users\Elvis\Projects\GestionScolaire\desktop\`)

### 2. Ouvrir dans VS Code
```
code GestionScolaire-Desktop
```

### 3. Lancer l'application (2 méthodes)

**Méthode A — Terminal (recommandé pour commencer)**
```bash
cd GestionScolaire-Desktop
mvn clean javafx:run
```

**Méthode B — VS Code**
Ouvre `Main.java`, clique sur "Run" au-dessus de la méthode `main()`
(l'extension Java gère automatiquement JavaFX si le pom.xml est bien pris en compte).

### 4. Ce que tu dois voir
Une fenêtre s'ouvre avec :
- ✅ Un en-tête bleu "🎓 Gestion Scolaire"
- ✅ Une barre latérale avec les menus (Étudiants, Classes, Notes...)
- ✅ Un message confirmant que la base SQLite locale a été créée, 
  avec le chemin exact du fichier `.db`
- ✅ Une barre de statut en bas (indicateur de sync, actuellement 
  "Hors ligne" car le SyncService n'est pas encore branché)

### 5. Vérifier la base SQLite créée
Le chemin est affiché dans l'application elle-même, généralement :
```
C:\Users\<TonNom>\AppData\Local\GestionScolaire\gestion_scolaire.db
```

Tu peux l'ouvrir avec **DBeaver** pour voir les tables déjà créées 
(classes, étudiants, notes, paiements, parents, etc. — tout le 
schéma qu'on a défini ensemble).

---

## 📁 Structure du projet

```
src/main/java/com/fasodev/gestionscolaire/
├── Main.java                  ← Point d'entrée
├── database/
│   ├── AppPaths.java           ← Gère où stocker les données (AppData)
│   ├── DatabaseConnection.java ← Connexion SQLite
│   └── DatabaseInitializer.java← Création du schéma complet (18 tables)
├── ui/controllers/
│   └── MainController.java     ← Contrôleur de la fenêtre principale
├── services/                   ← (vide, à remplir : logique métier)
├── repositories/                ← (vide, à remplir : accès aux données)
├── models/                      ← (vide, à remplir : entités Java)
└── utils/                       ← (vide, à remplir : PDF, CSV, etc.)

src/main/resources/
├── views/main.fxml              ← Interface principale
└── css/styles.css               ← Styles visuels
```

## ✅ Ce qui est déjà fonctionnel
- Squelette JavaFX complet et lançable
- Base SQLite créée automatiquement au premier lancement
- Schéma complet (18 tables) reflétant TOUTES les fonctionnalités validées :
  configuration école, subvention État, classes, matières, étudiants, 
  notes, contrôle continu, calcul moyennes verrouillable, passage classe 
  supérieure, examens nationaux, réinscriptions, frais/paiements, 
  identifiants parents, audit trail, sync queue

## ⏭️ Prochaines étapes (pas encore fait)
- Écrans CRUD (Étudiants, Classes, Notes...)
- Services métier (calcul moyenne, classement...)
- SyncService (connexion au Backend)
- Génération PDF (bulletins, reçus)
