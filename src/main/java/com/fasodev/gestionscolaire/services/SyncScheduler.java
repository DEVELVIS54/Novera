package com.fasodev.gestionscolaire.services;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Lance la synchronisation en arrière-plan toutes les 60 secondes et à la demande.
 * Le rappel reçoit (serveurJoignable, nombreEnAttente) pour mettre à jour la barre d'état.
 */
public final class SyncScheduler {

    private static final long INTERVALLE_SECONDES = 60;

    private static ScheduledExecutorService executeur;
    private static volatile BiConsumer<Boolean, Integer> rappel;
    private static final SyncService service = new SyncService();

    private SyncScheduler() {}

    public static synchronized void demarrer(BiConsumer<Boolean, Integer> nouveauRappel) {
        rappel = nouveauRappel;
        if (executeur != null) return;

        executeur = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "novera-sync");
            t.setDaemon(true);
            return t;
        });
        executeur.scheduleWithFixedDelay(SyncScheduler::passer, 3, INTERVALLE_SECONDES, TimeUnit.SECONDS);
    }

    /** Déclenche un passage immédiat (sans attendre le prochain cycle). */
    public static synchronized void declencher() {
        if (executeur != null) executeur.execute(SyncScheduler::passer);
    }

    private static void passer() {
        try {
            SyncService.Resultat r = service.synchroniser();
            BiConsumer<Boolean, Integer> cb = rappel;
            if (cb != null) cb.accept(r.serveurJoignable(), r.restantes());
        } catch (RuntimeException e) {
            // Une erreur ne doit jamais arrêter les passages suivants.
            System.err.println("Synchronisation : " + e.getMessage());
        }
    }
}
