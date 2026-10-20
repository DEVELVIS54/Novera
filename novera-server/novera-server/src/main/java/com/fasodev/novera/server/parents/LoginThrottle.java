package com.fasodev.novera.server.parents;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Limite les tentatives de connexion : 5 échecs par identifiant sur 10 minutes. */
@Component
public class LoginThrottle {

    static final int MAX_ECHECS = 5;
    static final long FENETRE_SECONDES = 10 * 60;

    private final ConcurrentHashMap<String, Deque<Long>> echecs = new ConcurrentHashMap<>();
    private final Clock horloge;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock horloge) {
        this.horloge = horloge;
    }

    public boolean estBloque(String identifiant) {
        Deque<Long> liste = echecs.get(cle(identifiant));
        if (liste == null) return false;
        synchronized (liste) {
            purger(liste);
            return liste.size() >= MAX_ECHECS;
        }
    }

    public void enregistrerEchec(String identifiant) {
        Deque<Long> liste = echecs.computeIfAbsent(cle(identifiant), k -> new ArrayDeque<>());
        synchronized (liste) {
            purger(liste);
            liste.addLast(horloge.instant().getEpochSecond());
        }
    }

    public void reinitialiser(String identifiant) {
        echecs.remove(cle(identifiant));
    }

    private void purger(Deque<Long> liste) {
        long limite = horloge.instant().getEpochSecond() - FENETRE_SECONDES;
        while (!liste.isEmpty() && liste.peekFirst() < limite) liste.removeFirst();
    }

    private static String cle(String identifiant) {
        return identifiant == null ? "" : identifiant.trim().toLowerCase();
    }
}
