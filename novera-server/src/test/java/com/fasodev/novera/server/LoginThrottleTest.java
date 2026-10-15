package com.fasodev.novera.server;

import com.fasodev.novera.server.parents.LoginThrottle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private LoginThrottle avec(Instant t) throws Exception {
        Constructor<LoginThrottle> c = LoginThrottle.class.getDeclaredConstructor(Clock.class);
        c.setAccessible(true);
        return c.newInstance(Clock.fixed(t, ZoneOffset.UTC));
    }

    @Test
    void bloque_apres_cinq_echecs() throws Exception {
        LoginThrottle l = avec(Instant.parse("2026-10-13T10:00:00Z"));
        for (int i = 0; i < 4; i++) l.enregistrerEchec("ECOL-001");
        assertFalse(l.estBloque("ECOL-001"));
        l.enregistrerEchec("ECOL-001");
        assertTrue(l.estBloque("ECOL-001"));
    }

    @Test
    void identifiants_independants_et_insensibles_a_la_casse() throws Exception {
        LoginThrottle l = avec(Instant.parse("2026-10-13T10:00:00Z"));
        for (int i = 0; i < 5; i++) l.enregistrerEchec("ECOL-001");
        assertTrue(l.estBloque("ecol-001"));
        assertFalse(l.estBloque("ECOL-002"));
    }

    @Test
    void reinitialisation_apres_succes() throws Exception {
        LoginThrottle l = avec(Instant.parse("2026-10-13T10:00:00Z"));
        for (int i = 0; i < 5; i++) l.enregistrerEchec("A");
        l.reinitialiser("A");
        assertFalse(l.estBloque("A"));
    }

    /** Horloge dont on peut faire avancer l'heure. */
    private static final class HorlogeMobile extends Clock {
        private Instant maintenant;
        HorlogeMobile(Instant depart) { this.maintenant = depart; }
        void avancer(long secondes) { maintenant = maintenant.plusSeconds(secondes); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    @Test
    void les_echecs_anciens_sont_oublies() throws Exception {
        HorlogeMobile horloge = new HorlogeMobile(Instant.parse("2026-10-13T10:00:00Z"));
        Constructor<LoginThrottle> c = LoginThrottle.class.getDeclaredConstructor(Clock.class);
        c.setAccessible(true);
        LoginThrottle l = c.newInstance(horloge);

        for (int i = 0; i < 5; i++) l.enregistrerEchec("A");
        assertTrue(l.estBloque("A"));

        horloge.avancer(9 * 60);   // 9 minutes : toujours bloqué
        assertTrue(l.estBloque("A"));

        horloge.avancer(2 * 60);   // 11 minutes au total : la fenêtre est passée
        assertFalse(l.estBloque("A"));
    }
}
