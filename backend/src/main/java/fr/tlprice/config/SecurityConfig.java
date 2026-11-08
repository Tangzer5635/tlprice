package fr.tlprice.config;

import fr.tlprice.repositories.UtilisateurRepository;
import fr.tlprice.services.ChiffrementService;
import fr.tlprice.services.ProtectionConnexionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Nom du cookie de connexion persistant. */
    private static final String COOKIE_CONNEXION = "tlprice-connexion";

    @Bean
    SecurityFilterChain chaine(HttpSecurity http,
                               UserDetailsService detailsService,
                               ChiffrementService chiffrement,
                               ProtectionConnexionService protection,
                               @Value("${tlprice.connexion.duree-heures:48}") int dureeHeures)
            throws Exception {

        CookieCsrfTokenRepository depotCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler gestionCsrf = new CsrfTokenRequestAttributeHandler();
        // Force la resolution du token a chaque requete pour que le cookie
        // XSRF-TOKEN soit toujours publie (necessaire pour une SPA)
        gestionCsrf.setCsrfRequestAttributeName(null);

        http
            // Frein au bruteforce : un client bloque recoit 429 avant tout BCrypt.
            .addFilterBefore(new FiltreLimiteConnexion(protection),
                UsernamePasswordAuthenticationFilter.class)
            .headers(entetes -> entetes
                // Tout est servi depuis la meme origine (polices comprises) :
                // la CSP peut etre stricte. 'unsafe-inline' reste necessaire
                // pour les attributs style="" poses par React. Les images
                // https sont permises pour les logos des banques.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; "
                    + "font-src 'self' data:; connect-src 'self'; worker-src 'self'; "
                    + "manifest-src 'self'; object-src 'none'; base-uri 'self'; "
                    + "form-action 'self'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=()")))
            .csrf(csrf -> csrf
                .csrfTokenRepository(depotCsrf)
                .csrfTokenRequestHandler(gestionCsrf))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/inscription", "/api/auth/login",
                    "/api/auth/config").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, a) -> {
                    protection.succesConnexion(req);
                    res.setStatus(200);
                })
                .failureHandler((req, res, e) -> {
                    protection.echecConnexion(req);
                    res.setStatus(401);
                }))
            /*
             * Connexion persistante.
             *
             * Sans ca, l authentification ne tient que par JSESSIONID, qui est
             * un cookie de session : iOS le jette quand on ferme la PWA, et il
             * disparait de toute facon a chaque redeploiement puisque les
             * sessions vivent en memoire. D ou la demande de mot de passe a
             * presque chaque ouverture.
             *
             * Le cookie pose ici porte une date d expiration explicite et une
             * signature verifiable sans etat cote serveur : il survit donc a la
             * fermeture de l app comme au redemarrage du conteneur.
             *
             * alwaysRemember : l app est personnelle et installee sur un
             * telephone. Demander « rester connecte ? » a chaque fois pour
             * toujours repondre oui n apporte rien.
             *
             * La cle de signature est derivee de TLPRICE_CLE_CHIFFREMENT. Si
             * cette variable change, tous les cookies emis deviennent invalides
             * et il faut se reconnecter une fois — c est le comportement voulu.
             */
            .rememberMe(memoire -> memoire
                .key(chiffrement.secretDerive("cookie-connexion"))
                .userDetailsService(detailsService)
                .alwaysRemember(true)
                .rememberMeCookieName(COOKIE_CONNEXION)
                .tokenValiditySeconds(dureeHeures * 3600))
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .deleteCookies(COOKIE_CONNEXION)
                .logoutSuccessHandler((req, res, a) -> res.setStatus(200)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.setStatus(401)));
        return http.build();
    }

    @Bean
    UserDetailsService detailsService(UtilisateurRepository repository) {
        return email -> repository.findByEmail(email.toLowerCase().trim())
            .map(u -> User.withUsername(u.getEmail())
                .password(u.getMotDePasseHash())
                .authorities("UTILISATEUR")
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Email inconnu"));
    }

    @Bean
    PasswordEncoder encodeur() {
        return new BCryptPasswordEncoder();
    }
}
