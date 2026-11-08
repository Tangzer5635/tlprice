package fr.tlprice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * En-tetes de cache adaptes a une PWA.
 *
 * - /assets/** : fichiers produits par Vite, dont le nom contient un hash du
 *   contenu. Ils ne changent jamais : un an, immutable. L iPhone ne les
 *   retelecharge plus a chaque ouverture, ce qui compte sur une connexion
 *   mobile vers un Raspberry Pi.
 * - tout le reste hors API (index.html, sw.js, manifest...) : toujours revalide. C est ce qui garantit
 *   qu un deploiement arrive sur le telephone, au lieu de rester bloque sur
 *   l ancienne version jusqu a ce qu iOS veuille bien vider son cache.
 * - /api/** : jamais mis en cache par le navigateur (donnees personnelles).
 *   Spring Security pose deja no-store, on ne touche a rien.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CacheStatique extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String uri = requete.getRequestURI();
        if (uri.startsWith("/assets/")) {
            reponse.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else if (!uri.startsWith("/api/") && !uri.startsWith("/actuator/")) {
            // index.html, sw.js, theme.js, manifest, icones : revalidation
            // systematique (un 304 ne coute presque rien).
            reponse.setHeader("Cache-Control", "no-cache");
        }
        chaine.doFilter(requete, reponse);
    }
}
