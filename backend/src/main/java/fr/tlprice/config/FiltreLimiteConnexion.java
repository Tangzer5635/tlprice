package fr.tlprice.config;

import fr.tlprice.services.ProtectionConnexionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Refuse en 429 les tentatives de connexion / inscription d un client deja
 * bloque, AVANT que Spring Security ne calcule le moindre BCrypt.
 */
public class FiltreLimiteConnexion extends OncePerRequestFilter {

    private final ProtectionConnexionService protection;

    public FiltreLimiteConnexion(ProtectionConnexionService protection) {
        this.protection = protection;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        if (!"POST".equals(requete.getMethod())) return true;
        String uri = requete.getRequestURI();
        return !uri.equals("/api/auth/login") && !uri.equals("/api/auth/inscription");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        boolean inscription = requete.getRequestURI().equals("/api/auth/inscription");
        Duration blocage = inscription
            ? protection.blocageInscription(requete)
            : protection.blocageConnexion(requete);

        if (blocage != null) {
            long minutes = Math.max(1, (blocage.getSeconds() + 59) / 60);
            reponse.setStatus(429);
            reponse.setHeader("Retry-After", String.valueOf(blocage.getSeconds()));
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("{\"message\":\"Trop de tentatives. Reessaie dans "
                + minutes + " minute" + (minutes > 1 ? "s" : "") + ".\"}");
            return;
        }
        if (inscription) protection.inscriptionTentee(requete);
        chaine.doFilter(requete, reponse);
    }
}
