package fr.tlprice.services;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freine le bruteforce sur la connexion et l inscription.
 *
 * L application est exposee sur Internet : sans frein, rien n empeche un
 * script d essayer des milliers de mots de passe par heure. BCrypt ralentit
 * chaque essai, mais sur un Raspberry Pi c est surtout le CPU de l app qui
 * paie la note.
 *
 * Deux compteurs independants, tous deux en memoire (perdus au redemarrage,
 * ce qui est acceptable : un attaquant ne gagne qu une fenetre) :
 *  - par email : 5 echecs en 15 min bloquent ce compte 15 min, quelle que
 *    soit l adresse IP. C est celui qui protege vraiment un mot de passe.
 *  - par IP : 20 echecs en 15 min bloquent l IP, pour qu un meme client ne
 *    puisse pas balayer une liste d emails.
 *
 * Une connexion reussie remet le compteur de l email a zero.
 */
@Service
public class ProtectionConnexionService {

    private static final Logger log = LoggerFactory.getLogger(ProtectionConnexionService.class);

    static final int MAX_ECHECS_EMAIL = 5;
    static final int MAX_ECHECS_IP = 20;
    static final int MAX_INSCRIPTIONS_IP = 5;
    static final Duration FENETRE = Duration.ofMinutes(15);
    static final Duration FENETRE_INSCRIPTION = Duration.ofHours(1);

    private record Compteur(int nombre, Instant debut) {}

    private final Map<String, Compteur> compteurs = new ConcurrentHashMap<>();

    /** Adresse du client. Derriere Cloudflare, l en-tete CF-Connecting-IP fait foi. */
    public static String ip(HttpServletRequest requete) {
        String cf = requete.getHeader("CF-Connecting-IP");
        return cf != null && !cf.isBlank() ? cf.trim() : requete.getRemoteAddr();
    }

    private static String email(HttpServletRequest requete) {
        String brut = requete.getParameter("username");
        return brut == null ? "" : brut.toLowerCase().trim();
    }

    /** Duree restante de blocage pour cette tentative de connexion, ou null. */
    public Duration blocageConnexion(HttpServletRequest requete) {
        Duration parIp = restant("ip:" + ip(requete), MAX_ECHECS_IP, FENETRE);
        if (parIp != null) return parIp;
        String email = email(requete);
        return email.isEmpty() ? null : restant("email:" + email, MAX_ECHECS_EMAIL, FENETRE);
    }

    public Duration blocageInscription(HttpServletRequest requete) {
        return restant("inscription:" + ip(requete), MAX_INSCRIPTIONS_IP, FENETRE_INSCRIPTION);
    }

    public void echecConnexion(HttpServletRequest requete) {
        String ip = ip(requete);
        String email = email(requete);
        incrementer("ip:" + ip, FENETRE);
        if (!email.isEmpty()) {
            int n = incrementer("email:" + email, FENETRE);
            if (n == MAX_ECHECS_EMAIL) {
                log.warn("Connexion bloquee {} min pour {} apres {} echecs (derniere IP {})",
                    FENETRE.toMinutes(), email, n, ip);
            }
        }
    }

    public void succesConnexion(HttpServletRequest requete) {
        String email = email(requete);
        if (!email.isEmpty()) compteurs.remove("email:" + email);
    }

    public void inscriptionTentee(HttpServletRequest requete) {
        incrementer("inscription:" + ip(requete), FENETRE_INSCRIPTION);
    }

    private int incrementer(String cle, Duration fenetre) {
        Instant maintenant = Instant.now();
        return compteurs.compute(cle, (k, c) ->
            c == null || c.debut().plus(fenetre).isBefore(maintenant)
                ? new Compteur(1, maintenant)
                : new Compteur(c.nombre() + 1, c.debut())).nombre();
    }

    private Duration restant(String cle, int max, Duration fenetre) {
        Compteur c = compteurs.get(cle);
        if (c == null || c.nombre() < max) return null;
        Duration reste = Duration.between(Instant.now(), c.debut().plus(fenetre));
        return reste.isNegative() || reste.isZero() ? null : reste;
    }

    /** Menage periodique, pour que la table ne grossisse pas indefiniment. */
    @Scheduled(fixedDelay = 600_000)
    public void purger() {
        Instant limite = Instant.now().minus(FENETRE_INSCRIPTION);
        compteurs.values().removeIf(c -> c.debut().isBefore(limite));
    }
}
