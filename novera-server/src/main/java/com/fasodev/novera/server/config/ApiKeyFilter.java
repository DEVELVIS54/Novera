package com.fasodev.novera.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protège les routes réservées au desktop (/api/sync/** et /api/records/**)
 * par une clé d'accès partagée, envoyée dans l'en-tête X-Api-Key.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] cleAttendue;

    public ApiKeyFilter(@Value("${novera.api-key}") String cle) {
        if (cle == null || cle.isBlank()) {
            throw new IllegalStateException("novera.api-key (NOVERA_API_KEY) doit être défini.");
        }
        this.cleAttendue = cle.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String chemin = request.getRequestURI();
        return !(chemin.startsWith("/api/sync/") || chemin.startsWith("/api/records/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chaine) throws ServletException, IOException {
        String recue = request.getHeader("X-Api-Key");
        boolean valide = recue != null
            && MessageDigest.isEqual(recue.getBytes(StandardCharsets.UTF_8), cleAttendue);

        if (!valide) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Clé d'accès invalide\"}");
            return;
        }
        chaine.doFilter(request, response);
    }
}
