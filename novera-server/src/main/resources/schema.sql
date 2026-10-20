-- Réplique des données du desktop : une ligne par enregistrement synchronisé.
CREATE TABLE IF NOT EXISTS sync_records (
    table_name  VARCHAR(64)  NOT NULL,
    record_id   BIGINT       NOT NULL,
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    device_id   VARCHAR(64),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (table_name, record_id)
);

CREATE INDEX IF NOT EXISTS idx_sync_records_etudiant
    ON sync_records (table_name, ((payload->>'etudiant_id')));
CREATE INDEX IF NOT EXISTS idx_sync_records_classe
    ON sync_records (table_name, ((payload->>'classe_id')));
CREATE INDEX IF NOT EXISTS idx_sync_records_parent_identifiant
    ON sync_records ((payload->>'identifiant')) WHERE table_name = 'parents';
