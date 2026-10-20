package com.fasodev.novera.server.sync;

import com.fasodev.novera.server.sync.SyncModels.Operation;
import com.fasodev.novera.server.sync.SyncModels.Refus;
import com.fasodev.novera.server.sync.SyncModels.Reponse;
import com.fasodev.novera.server.sync.SyncModels.Requete;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Applique les opérations du desktop dans la table sync_records.
 * Les opérations sont idempotentes (upsert) : renvoyer un lot déjà reçu est sans danger.
 * Elles sont traitées dans l'ordre : la dernière version d'une ligne l'emporte.
 */
@Service
public class SyncService {

    private static final String UPSERT = """
        INSERT INTO sync_records (table_name, record_id, payload, deleted, device_id, updated_at)
        VALUES (?, ?, ?::jsonb, FALSE, ?, now())
        ON CONFLICT (table_name, record_id) DO UPDATE
        SET payload = EXCLUDED.payload, deleted = FALSE,
            device_id = EXCLUDED.device_id, updated_at = now()
        """;

    private static final String SUPPRESSION = """
        INSERT INTO sync_records (table_name, record_id, payload, deleted, device_id, updated_at)
        VALUES (?, ?, '{}'::jsonb, TRUE, ?, now())
        ON CONFLICT (table_name, record_id) DO UPDATE
        SET deleted = TRUE, device_id = EXCLUDED.device_id, updated_at = now()
        """;

    private final JdbcTemplate jdbc;

    public SyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Reponse appliquer(Requete requete) {
        List<Long> acceptees = new ArrayList<>();
        List<Refus> refusees = new ArrayList<>();

        String deviceId = requete.deviceId() != null ? requete.deviceId() : "inconnu";
        List<Operation> operations = requete.operations() != null ? requete.operations() : List.of();

        for (Operation op : operations) {
            boolean aUnPayload = op.payload() != null && op.payload().isObject();
            String motif = SyncValidator.valider(op.table(), op.operation(), op.recordId(), aUnPayload);
            if (motif != null) {
                refusees.add(new Refus(op.queueId(), motif));
                continue;
            }

            if ("DELETE".equals(op.operation())) {
                jdbc.update(SUPPRESSION, op.table(), op.recordId(), deviceId);
            } else {
                jdbc.update(UPSERT, op.table(), op.recordId(), op.payload().toString(), deviceId);
            }
            acceptees.add(op.queueId());
        }
        return new Reponse(acceptees, refusees);
    }
}
