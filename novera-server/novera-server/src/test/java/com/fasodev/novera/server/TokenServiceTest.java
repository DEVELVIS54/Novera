package com.fasodev.novera.server;

import com.fasodev.novera.server.parents.TokenService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private static final String SECRET = "un-secret-de-test-assez-long";

    private TokenService service(String secret, Instant maintenant) throws Exception {
        Constructor<TokenService> c = TokenService.class.getDeclaredConstructor(String.class, Clock.class);
        c.setAccessible(true);
        return c.newInstance(secret, Clock.fixed(maintenant, ZoneOffset.UTC));
    }

    @Test
    void jeton_valide_redonne_l_identifiant() throws Exception {
        TokenService s = service(SECRET, Instant.parse("2026-10-13T10:00:00Z"));
        assertEquals(42, s.verifier(s.creer(42)));
    }

    @Test
    void jeton_expire_apres_deux_heures() throws Exception {
        String jeton = service(SECRET, Instant.parse("2026-10-13T10:00:00Z")).creer(7);
        assertEquals(7, service(SECRET, Instant.parse("2026-10-13T11:59:00Z")).verifier(jeton));
        assertEquals(-1, service(SECRET, Instant.parse("2026-10-13T12:01:00Z")).verifier(jeton));
    }

    @Test
    void jeton_modifie_ou_signe_avec_un_autre_secret_est_refuse() throws Exception {
        Instant t = Instant.parse("2026-10-13T10:00:00Z");
        String jeton = service(SECRET, t).creer(7);

        String falsifie = "8" + jeton.substring(jeton.indexOf('.'));
        assertEquals(-1, service(SECRET, t).verifier(falsifie));
        assertEquals(-1, service("un-tout-autre-secret-long", t).verifier(jeton));
    }

    @Test
    void jetons_mal_formes_sont_refuses() throws Exception {
        TokenService s = service(SECRET, Instant.parse("2026-10-13T10:00:00Z"));
        assertEquals(-1, s.verifier(null));
        assertEquals(-1, s.verifier(""));
        assertEquals(-1, s.verifier("a.b"));
        assertEquals(-1, s.verifier("x.y.z"));
    }

    @Test
    void secret_trop_court_est_refuse() {
        assertThrows(Exception.class, () -> service("court", Instant.now()));
    }
}
