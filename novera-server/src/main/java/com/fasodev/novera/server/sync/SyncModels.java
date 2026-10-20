package com.fasodev.novera.server.sync;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Formats JSON échangés avec le desktop. */
public final class SyncModels {

    private SyncModels() {}

    public record Operation(long queueId, String table, String operation, long recordId, JsonNode payload) {}

    public record Requete(String deviceId, List<Operation> operations) {}

    public record Refus(long queueId, String reason) {}

    public record Reponse(List<Long> accepted, List<Refus> rejected) {}
}
