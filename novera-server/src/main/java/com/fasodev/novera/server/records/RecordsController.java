package com.fasodev.novera.server.records;

import com.fasodev.novera.server.sync.SyncValidator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Lecture des données synchronisées (réservée à la clé d'accès). Sert de base à l'extension web. */
@RestController
public class RecordsController {

    private final JdbcTemplate jdbc;

    public RecordsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/records/{table}")
    public List<Map<String, Object>> lister(@PathVariable String table,
                                            @RequestParam(defaultValue = "200") int limit,
                                            @RequestParam(defaultValue = "0") long after) {
        if (!SyncValidator.TABLES_AUTORISEES.contains(table)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table inconnue");
        }
        int borne = Math.max(1, Math.min(limit, 1000));
        return jdbc.queryForList("""
            SELECT record_id, payload::text AS payload, updated_at
            FROM sync_records
            WHERE table_name = ? AND deleted = FALSE AND record_id > ?
            ORDER BY record_id
            LIMIT ?
            """, table, after, borne);
    }
}
