package com.fasodev.novera.server.parents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * Jetons de session de l'espace parents : "identifiant.expiration.signature" (HMAC-SHA256).
 * Sans état côté serveur ; le jeton expire tout seul.
 */
@Component
public class TokenService {

    private static final long DUREE_SECONDES = 2 * 60 * 60;

    private final byte[] secret;
    private final Clock horloge;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenService(@Value("${novera.token-secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    TokenService(String secret, Clock horloge) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("novera.token-secret (NOVERA_TOKEN_SECRET) doit faire au moins 16 caractères.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.horloge = horloge;
    }

    public String creer(long parentId) {
        long expiration = horloge.instant().getEpochSecond() + DUREE_SECONDES;
        String corps = parentId + "." + expiration;
        return corps + "." + signer(corps);
    }

    /** Retourne l'identifiant du parent si le jeton est valide et non expiré, sinon -1. */
    public long verifier(String jeton) {
        if (jeton == null) return -1;
        String[] parties = jeton.split("\\.");
        if (parties.length != 3) return -1;

        String corps = parties[0] + "." + parties[1];
        byte[] attendue = signer(corps).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(attendue, parties[2].getBytes(StandardCharsets.UTF_8))) return -1;

        try {
            long expiration = Long.parseLong(parties[1]);
            if (horloge.instant().getEpochSecond() > expiration) return -1;
            return Long.parseLong(parties[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String signer(String texte) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signature impossible", e);
        }
    }
}
