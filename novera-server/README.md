# Novera — Serveur (Spring Boot)

Reçoit la synchronisation du desktop, la stocke dans PostgreSQL et expose un **espace parents**
en lecture seule (`/parents/`).

## Démarrer avec Docker

```bash
export NOVERA_API_KEY="$(openssl rand -hex 24)"
export NOVERA_TOKEN_SECRET="$(openssl rand -hex 32)"
docker compose up --build
```

Serveur sur http://localhost:8080 — espace parents sur http://localhost:8080/parents/.
Dans le desktop (Synchronisation), saisir l'adresse du serveur et la valeur de `NOVERA_API_KEY`.

## Sans Docker

Java 21, Maven, une base PostgreSQL, puis :

```bash
export NOVERA_DB_URL=jdbc:postgresql://localhost:5432/novera NOVERA_DB_USER=novera NOVERA_DB_PASSWORD=...
export NOVERA_API_KEY=... NOVERA_TOKEN_SECRET=...   # secret : 16 caractères minimum
mvn spring-boot:run
```

`NOVERA_API_KEY` et `NOVERA_TOKEN_SECRET` sont obligatoires : le serveur refuse de démarrer sans.

## API

| Route | Accès | Rôle |
|---|---|---|
| `GET /api/health` | public | Le serveur répond-il ? |
| `GET /api/sync/ping` | clé `X-Api-Key` | Test de la clé |
| `POST /api/sync/batch` | clé | Reçoit un lot `{deviceId, operations[]}` ; répond `{accepted[], rejected[]}` |
| `GET /api/records/{table}?limit=&after=` | clé | Lit les données synchronisées |
| `POST /api/parents/login` | public (5 échecs / 10 min) | Identifiant + mot de passe → jeton 2 h |
| `GET /api/parents/me` | jeton parent | Élève, moyennes, total payé et frais de la classe |

## Stockage

Une seule table `sync_records (table_name, record_id, payload jsonb, deleted, …)`. Les opérations
sont des *upserts* : renvoyer un lot est sans danger. Les suppressions sont conservées (`deleted = true`).
Seules 14 tables métier sont acceptées ; les comptes utilisateurs du desktop n'en font pas partie.

## Tests

`mvn test` — tests de la validation des opérations, des jetons et de la limitation de tentatives.

## Limites actuelles

- Un poste desktop par école (voir le README du desktop).
- L'espace parents affiche le total payé et les frais de la classe, sans calculer le solde
  (les réductions de subvention ne sont pas appliquées côté serveur).
- Prévoir HTTPS devant le serveur (reverse proxy) avant toute mise en production.
