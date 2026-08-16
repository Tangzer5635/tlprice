package fr.tlprice.services;

import fr.tlprice.entities.ConnexionBancaire;
import fr.tlprice.entities.Parametres;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ConnexionBancaireRepository;
import fr.tlprice.repositories.ParametresRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Rafraichit le solde bancaire AVANT les envois quotidiens.
 *
 * Sans ca, le recap de 7h calcule sa projection a partir du dernier solde
 * synchronise a la main : si l app n a pas ete ouverte depuis trois jours,
 * la notification annonce un chiffre perime sans que rien ne le signale.
 * C est le pire des cas — une information fausse qui a l air juste.
 *
 * L horaire est cale une heure avant chaque envoi, a la demi.
 */
@Service
public class SynchronisationAutoService {

    private static final Logger log = LoggerFactory.getLogger(SynchronisationAutoService.class);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** On previent une fois par semaine au plus quand le consentement expire. */
    private static final int JOURS_AVANT_EXPIRATION = 7;

    private final ParametresRepository parametresRepository;
    private final ConnexionBancaireRepository connexionRepository;
    private final EnableBankingService enableBankingService;
    private final RapprochementService rapprochementService;
    private final HistoriqueSoldeService historiqueService;
    private final PushService pushService;

    public SynchronisationAutoService(ParametresRepository parametresRepository,
                                      ConnexionBancaireRepository connexionRepository,
                                      EnableBankingService enableBankingService,
                                      RapprochementService rapprochementService,
                                      HistoriqueSoldeService historiqueService,
                                      PushService pushService) {
        this.parametresRepository = parametresRepository;
        this.connexionRepository = connexionRepository;
        this.enableBankingService = enableBankingService;
        this.rapprochementService = rapprochementService;
        this.historiqueService = historiqueService;
        this.pushService = pushService;
    }

    /**
     * Toutes les heures a la demi. On synchronise les utilisateurs dont un
     * envoi (push ou mail) est prevu a l heure suivante.
     */
    @Scheduled(cron = "0 30 * * * *", zone = "Europe/Paris")
    public void synchroniserAvantEnvoi() {
        int heureSuivante = (LocalTime.now(PARIS).getHour() + 1) % 24;

        for (Parametres p : parametresRepository.findAll()) {
            boolean envoiPrevu =
                (p.isPushActif() && p.getHeureRecapPush() == heureSuivante)
                || (p.isAlertesEmail() && p.getHeureRecap() == heureSuivante);
            if (!envoiPrevu) continue;

            Utilisateur utilisateur = p.getUtilisateur();
            Optional<ConnexionBancaire> connexion =
                connexionRepository.findByUtilisateur(utilisateur);
            if (connexion.isEmpty()) continue;

            if (alerterSiConsentementFinit(utilisateur, connexion.get())) continue;

            try {
                enableBankingService.synchroniser(utilisateur);
                historiqueService.capturer(utilisateur);
                log.info("Solde synchronise automatiquement pour {}", utilisateur.getEmail());
                // Tant qu on tient une connexion ouverte, on en profite pour
                // confronter les echeances passees a ce qui a ete debite.
                try {
                    rapprochementService.analyser(utilisateur, 45);
                } catch (Exception e) {
                    log.warn("Rapprochement auto echoue pour {} : {}",
                        utilisateur.getEmail(), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Synchronisation auto echouee pour {} : {}",
                    utilisateur.getEmail(), e.getMessage());
                pushService.envoyer(utilisateur,
                    "Synchronisation impossible",
                    "Le solde n a pas pu etre rafraichi. Le montant affiche date de la "
                        + "derniere synchro reussie.",
                    "/banque");
            }
        }
    }

    /**
     * Le consentement Enable Banking dure 90 jours et son expiration est le
     * mode de panne le plus vicieux : la synchro echoue, le solde se fige, et
     * rien ne le dit. Renvoie vrai si le consentement est deja expire (inutile
     * de tenter la synchro).
     */
    private boolean alerterSiConsentementFinit(Utilisateur utilisateur,
                                               ConnexionBancaire connexion) {
        OffsetDateTime maintenant = OffsetDateTime.now(ZoneOffset.UTC);
        if (connexion.getValidUntil().isBefore(maintenant)) {
            pushService.envoyer(utilisateur,
                "Connexion bancaire expiree",
                "Le consentement de 90 jours est arrive a terme. Reconnecte ta banque "
                    + "pour que le solde reprenne sa mise a jour.",
                "/banque");
            return true;
        }
        long joursRestants = ChronoUnit.DAYS.between(maintenant, connexion.getValidUntil());
        if (joursRestants == JOURS_AVANT_EXPIRATION) {
            pushService.envoyer(utilisateur,
                "Connexion bancaire a renouveler",
                "Ton consentement expire dans " + JOURS_AVANT_EXPIRATION
                    + " jours. Un clic dans l onglet Banque suffit.",
                "/banque");
        }
        return false;
    }
}
