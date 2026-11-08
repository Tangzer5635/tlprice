package fr.tlprice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class TlpriceApplication {

    public static void main(String[] args) {
        fixerFuseau();
        SpringApplication.run(TlpriceApplication.class, args);
    }

    /**
     * Le conteneur tourne en UTC. Or une bonne partie du code (projection,
     * capacite d epargne, mise a jour du solde) appelle LocalDate.now() sans
     * fuseau : entre minuit et 1 h / 2 h du matin heure de Paris, « aujourd hui »
     * etait encore la veille, et la projection affichait un jour de decalage.
     *
     * Fixer le fuseau par defaut de la JVM corrige tous ces appels d un coup,
     * y compris ceux qu on ajoutera plus tard. Surchargeable par TLPRICE_FUSEAU.
     */
    static void fixerFuseau() {
        String fuseau = System.getenv().getOrDefault("TLPRICE_FUSEAU", "Europe/Paris");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(fuseau)));
        } catch (Exception e) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/Paris")));
        }
    }
}
