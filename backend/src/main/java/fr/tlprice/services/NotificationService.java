package fr.tlprice.services;

import fr.tlprice.dto.AlerteSoldeDto;
import fr.tlprice.dto.MouvementDto;
import fr.tlprice.entities.AbonnementPush;
import fr.tlprice.repositories.AbonnementPushRepository;
import fr.tlprice.dto.SoldeProjeteDto;
import fr.tlprice.entities.ConfigSmtp;
import fr.tlprice.entities.Parametres;
import fr.tlprice.entities.TypeOperation;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.entities.Transaction;
import fr.tlprice.repositories.ParametresRepository;
import fr.tlprice.repositories.TransactionRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Recap quotidien envoye a l heure choisie par chaque utilisateur (fuseau
 * Europe/Paris), au format HTML aux couleurs de tlprice.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter FR =
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
    private static final DateTimeFormatter JOUR_COURT =
        DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final ParametresRepository parametresRepository;
    private final SoldeService soldeService;
    private final EcheanceService echeanceService;
    private final TransactionRepository transactionRepository;
    private final PushService pushService;
    private final AbonnementPushRepository abonnementRepository;
    private final RapprochementService rapprochementService;
    private final SmtpUtilisateurService smtpUtilisateurService;
    private final JavaMailSender senderGlobal;
    private final String expediteurGlobal;
    private final String motDePasseGlobal;

    public NotificationService(ParametresRepository parametresRepository,
                               SoldeService soldeService,
                               EcheanceService echeanceService,
                               TransactionRepository transactionRepository,
                               PushService pushService,
                               AbonnementPushRepository abonnementRepository,
                               RapprochementService rapprochementService,
                               SmtpUtilisateurService smtpUtilisateurService,
                               @Autowired(required = false) JavaMailSender senderGlobal,
                               @Value("${tlprice.mail.from:}") String expediteurGlobal,
                               @Value("${spring.mail.password:}") String motDePasseGlobal) {
        this.parametresRepository = parametresRepository;
        this.soldeService = soldeService;
        this.echeanceService = echeanceService;
        this.transactionRepository = transactionRepository;
        this.pushService = pushService;
        this.abonnementRepository = abonnementRepository;
        this.rapprochementService = rapprochementService;
        this.smtpUtilisateurService = smtpUtilisateurService;
        this.senderGlobal = senderGlobal;
        this.expediteurGlobal = expediteurGlobal;
        this.motDePasseGlobal = motDePasseGlobal;
    }

    public boolean mailDisponible(Utilisateur utilisateur) {
        return smtpUtilisateurService.chercher(utilisateur).isPresent()
            || (senderGlobal != null
                && expediteurGlobal != null && !expediteurGlobal.isBlank()
                && motDePasseGlobal != null && !motDePasseGlobal.isBlank());
    }

    /** Envoi ponctuel (bouton "test"). */
    public String envoyerRecap(Utilisateur utilisateur) {
        if (!mailDisponible(utilisateur)) {
            return "Configure d abord ton SMTP dans les parametres.";
        }
        Parametres p = soldeService.parametres(utilisateur);
        String destinataire = p.getEmail() != null && !p.getEmail().isBlank()
            ? p.getEmail() : utilisateur.getEmail();
        try {
            envoyer(utilisateur, destinataire);
            return "Recap envoye a " + destinataire;
        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("Auth SMTP echouee", e);
            return "Authentification SMTP refusee. Verifie ton nom d utilisateur et ton mot de passe. "
                + "Pour Gmail il faut un mot de passe d application (16 caracteres), pas ton mot de passe habituel.";
        } catch (Exception e) {
            log.error("Echec envoi mail a {}", destinataire, e);
            return "Envoi echoue : " + e.getMessage();
        }
    }

    /**
     * S execute chaque heure. Pour chaque utilisateur ayant active le recap
     * EMAIL, on envoie si l heure Paris correspond a son heureRecap ET qu on
     * n a pas deja envoye aujourd hui.
     *
     * Le push a son propre ordonnanceur ci-dessous, avec sa propre heure.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Paris")
    public void recapEmailHoraire() {
        int heureActuelle = LocalTime.now(PARIS).getHour();
        LocalDate aujourdhui = LocalDate.now(PARIS);
        for (Parametres p : parametresRepository.findByAlertesEmailTrue()) {
            if (p.getHeureRecap() != heureActuelle) continue;
            if (aujourdhui.equals(p.getDernierRecap())) continue;
            Utilisateur u = p.getUtilisateur();
            if (!mailDisponible(u)) continue;
            String destinataire = p.getEmail() != null && !p.getEmail().isBlank()
                ? p.getEmail() : u.getEmail();
            try {
                envoyer(u, destinataire);
                p.setDernierRecap(aujourdhui);
                parametresRepository.save(p);
            } catch (Exception e) {
                log.warn("Echec recap pour {} : {}", destinataire, e.getMessage());
            }
        }
    }

    // =========================================================================
    // Push (Web Push / iOS)
    // =========================================================================

    public boolean pushDisponible() {
        return pushService.disponible();
    }

    /**
     * S execute chaque heure. Depuis la v4, l unite n est plus le compte mais
     * l APPAREIL : chaque abonnement porte sa propre heure de recap, et on
     * n envoie qu a ceux dont l heure correspond a maintenant.
     *
     * Le cas d usage : l iPhone previent a midi, le PC de bureau a 22h. Avec
     * une heure unique par compte, l un des deux tombait toujours au mauvais
     * moment.
     *
     * L heure du compte reste le defaut. Un appareil dont heureRecap est NULL
     * la suit — c est l etat de tous les appareils existants apres migration,
     * donc le comportement ne change pour personne tant que rien n est regle.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Paris")
    public void pushQuotidienHoraire() {
        if (!pushService.disponible()) return;
        int heureActuelle = LocalTime.now(PARIS).getHour();
        LocalDate aujourdhui = LocalDate.now(PARIS);

        for (Parametres p : parametresRepository.findByPushActifTrue()) {
            Utilisateur u = p.getUtilisateur();
            int heureDuCompte = p.getHeureRecapPush();

            List<AbonnementPush> cibles = new ArrayList<>();
            for (AbonnementPush a : abonnementRepository.findByUtilisateur(u)) {
                if (a.heureEffective(heureDuCompte) != heureActuelle) continue;
                if (aujourdhui.equals(a.getDernierPush())) continue;
                cibles.add(a);
            }
            if (cibles.isEmpty()) continue;

            try {
                boolean envoye = pousserJournee(u, p.isPushSiVide(), cibles);
                // On marque la journee meme si rien n a ete envoye faute de
                // mouvement : sinon on retenterait a chaque heure suivante.
                for (AbonnementPush a : cibles) {
                    a.setDernierPush(aujourdhui);
                }
                abonnementRepository.saveAll(cibles);
                if (envoye) {
                    log.debug("Push quotidien envoye a {} sur {} appareil(s)",
                        u.getEmail(), cibles.size());
                }
            } catch (Exception e) {
                log.warn("Echec push quotidien pour {} : {}", u.getEmail(), e.getMessage());
            }
        }
    }

    /** Bouton "envoyer un test" des reglages. */
    public String envoyerPushTest(Utilisateur utilisateur) {
        if (!pushService.disponible()) {
            return "Le push n est pas configure sur ce serveur (cles VAPID absentes).";
        }
        if (!pushService.abonne(utilisateur)) {
            return "Aucun appareil abonne. Active les notifications sur ton iPhone d abord.";
        }
        int envoyes = pousserJournee(utilisateur, true) ? 1 : 0;
        return envoyes > 0
            ? "Notification envoyee. Si rien n arrive sur ton iPhone, verifie que "
                + "l app a bien ete ajoutee a l ecran d accueil."
            : "Envoi refuse par le service de push. Reabonne cet appareil.";
    }

    /**
     * Notification des mouvements du jour. Renvoie faux si rien n a ete
     * envoye (aucun mouvement et pushSiVide desactive, ou aucun appareil).
     */
    private boolean pousserJournee(Utilisateur utilisateur, boolean memeSiVide) {
        return pousserJournee(utilisateur, memeSiVide,
            abonnementRepository.findByUtilisateur(utilisateur));
    }

    private boolean pousserJournee(Utilisateur utilisateur, boolean memeSiVide,
                                   List<AbonnementPush> cibles) {
        LocalDate aujourdhui = LocalDate.now(PARIS);
        List<MouvementDto> duJour = mouvementsDuJour(utilisateur, aujourdhui);
        if (duJour.isEmpty() && !memeSiVide) return false;

        SoldeProjeteDto projection =
            soldeService.calculerSoldeProjete(utilisateur, aujourdhui.plusDays(7));

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (MouvementDto m : duJour) {
            if (m.getType() == TypeOperation.DEBIT) debits = debits.add(m.getMontant());
            else credits = credits.add(m.getMontant());
        }

        String titre;
        if (duJour.isEmpty()) {
            titre = "Rien aujourd hui";
        } else if (debits.signum() > 0 && credits.signum() > 0) {
            titre = fmt(debits) + " preleves, " + fmt(credits) + " recus";
        } else if (debits.signum() > 0) {
            titre = duJour.size() + (duJour.size() > 1 ? " prelevements" : " prelevement")
                + " · " + fmt(debits);
        } else {
            titre = "Credit de " + fmt(credits);
        }

        StringBuilder corps = new StringBuilder();
        int montres = 0;
        for (MouvementDto m : duJour) {
            if (montres == 3) {
                corps.append(" · +").append(duJour.size() - 3).append(" autre")
                     .append(duJour.size() - 3 > 1 ? "s" : "");
                break;
            }
            if (montres > 0) corps.append(" · ");
            corps.append(m.getType() == TypeOperation.CREDIT ? "+" : "−")
                 .append(" ").append(m.getLibelle());
            montres++;
        }
        if (corps.length() > 0) corps.append("\n");
        corps.append("Projete a 7 jours : ").append(fmt(projection.getSoldeProjete()));

        AlerteSoldeDto alerte = soldeService.verifierSeuil(utilisateur);
        if (alerte.isAlerte()) {
            corps.append(" · sous ton seuil le ")
                 .append(alerte.getDateMinimum().format(JOUR_COURT));
        }

        long ecarts = rapprochementService.nombreEnAttente(utilisateur);
        if (ecarts > 0) {
            corps.append("\n").append(ecarts)
                 .append(ecarts > 1 ? " ecarts a verifier" : " ecart a verifier");
        }

        return pushService.envoyerA(cibles, titre, corps.toString(), "/") > 0;
    }

    /**
     * Mouvements tombant aujourd hui : echeances recurrentes + transactions
     * ponctuelles pas encore debitees. On interroge EcheanceService en direct
     * plutot que la projection, car celle-ci demarre a dateMaj+1 et masquerait
     * la journee si le solde vient d etre synchronise.
     */
    private List<MouvementDto> mouvementsDuJour(Utilisateur utilisateur, LocalDate jour) {
        List<MouvementDto> liste = new ArrayList<>(
            echeanceService.genererEcheances(utilisateur, jour, jour));
        for (Transaction tr : transactionRepository
                .findByUtilisateurAndDebiteeEnBanqueFalse(utilisateur)) {
            if (tr.getDateOperation().equals(jour)) {
                liste.add(new MouvementDto(tr.getDateOperation(), tr.getLibelle(),
                    tr.getMontant(), tr.getType(), tr.getCategorie(), "TRANSACTION", null, null));
            }
        }
        liste.sort(Comparator.comparing(MouvementDto::getDate));
        return liste;
    }

    private void envoyer(Utilisateur utilisateur, String destinataire) throws Exception {
        Optional<ConfigSmtp> configUser = smtpUtilisateurService.chercher(utilisateur);
        JavaMailSender sender;
        String expediteur;
        if (configUser.isPresent()) {
            ConfigSmtp c = configUser.get();
            sender = smtpUtilisateurService.construireSender(c);
            expediteur = c.getExpediteur() != null && !c.getExpediteur().isBlank()
                ? c.getExpediteur() : c.getUsername();
        } else {
            sender = senderGlobal;
            expediteur = expediteurGlobal;
        }

        LocalDate aujourdhui = LocalDate.now(PARIS);
        LocalDate dans7j = aujourdhui.plusDays(7);
        SoldeProjeteDto projection = soldeService.calculerSoldeProjete(utilisateur, dans7j);
        AlerteSoldeDto alerte = soldeService.verifierSeuil(utilisateur);

        String sujet = "tlprice · " + fmt(projection.getSoldeProjete())
            + " projetes a 7 jours" + (alerte.isAlerte() ? " (alerte)" : "");
        String html = construireHtml(projection, alerte, aujourdhui);

        MimeMessage mime = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
        helper.setFrom(expediteur);
        helper.setTo(destinataire);
        helper.setSubject(sujet);
        helper.setText(html, true);
        sender.send(mime);
    }

    // =========================================================================
    // Construction du HTML
    // =========================================================================

    /** Palette figee, calquee sur le theme sombre du site. */
    private static final String BRUME = "#0f1613";
    private static final String PAPIER = "#182320";
    private static final String ENCRE = "#e7ede9";
    private static final String ENCRE_DOUCE = "#92a19b";
    private static final String FILET = "#2a3733";
    private static final String CREDIT = "#4dbf90";
    private static final String DEBIT = "#e8735f";
    private static final String ALERTE_FOND = "#2e1c18";
    private static final String POLICE = "Archivo, -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif";
    private static final String POLICE_MONO = "\"IBM Plex Mono\", ui-monospace, Menlo, Consolas, monospace";

    private String construireHtml(SoldeProjeteDto projection, AlerteSoldeDto alerte,
                                  LocalDate aujourdhui) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"fr\"><head><meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
          .append("<title>tlprice</title></head>")
          .append("<body style=\"margin:0;padding:0;background:").append(BRUME)
          .append(";font-family:").append(POLICE).append(";color:").append(ENCRE).append(";\">");

        // Wrapper
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
          .append("style=\"background:").append(BRUME).append(";padding:32px 16px;\">")
          .append("<tr><td align=\"center\">")
          .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
          .append("style=\"max-width:600px;width:100%;\">");

        // Header : marque
        sb.append("<tr><td style=\"padding:0 8px 24px;\">")
          .append("<span style=\"font-size:26px;font-weight:800;letter-spacing:-0.02em;color:")
          .append(ENCRE).append(";\">tlprice<span style=\"color:").append(CREDIT)
          .append(";\">·</span></span>")
          .append("<span style=\"float:right;font-size:12px;color:").append(ENCRE_DOUCE)
          .append(";text-transform:uppercase;letter-spacing:0.08em;padding-top:10px;\">")
          .append(aujourdhui.format(FR)).append("</span></td></tr>");

        // Hero : solde projete
        boolean negatif = projection.getSoldeProjete().signum() < 0;
        sb.append("<tr><td style=\"background:").append(PAPIER)
          .append(";border-radius:16px;padding:32px 24px;\">")
          .append("<p style=\"margin:0 0 4px;font-size:12px;text-transform:uppercase;")
          .append("letter-spacing:0.08em;color:").append(ENCRE_DOUCE)
          .append(";font-weight:600;\">Solde projete a 7 jours</p>")
          .append("<p style=\"margin:0 0 20px;font-size:44px;font-weight:600;color:")
          .append(negatif ? DEBIT : CREDIT).append(";font-family:").append(POLICE_MONO)
          .append(";letter-spacing:-0.03em;\">").append(fmt(projection.getSoldeProjete()))
          .append("</p>")
          .append("<p style=\"margin:0;font-size:14px;color:").append(ENCRE_DOUCE).append(";\">")
          .append("Solde banque : <strong style=\"color:").append(ENCRE).append(";\">")
          .append(fmt(projection.getSoldeReel())).append("</strong> au ")
          .append(projection.getDateMaj().format(JOUR_COURT))
          .append("<br><span style=\"font-family:").append(POLICE_MONO).append(";color:")
          .append(CREDIT).append(";\">+ ").append(fmt(projection.getTotalCredits())).append("</span>")
          .append("&nbsp;&nbsp;<span style=\"font-family:").append(POLICE_MONO).append(";color:")
          .append(DEBIT).append(";\">− ").append(fmt(projection.getTotalDebits())).append("</span></p>")
          .append("</td></tr>");

        // Alerte seuil (si applicable)
        if (alerte.isAlerte()) {
            sb.append("<tr><td style=\"padding-top:16px;\">")
              .append("<div style=\"background:").append(ALERTE_FOND)
              .append(";border-left:3px solid ").append(DEBIT)
              .append(";padding:14px 18px;color:").append(DEBIT)
              .append(";border-radius:0 8px 8px 0;font-size:14px;\">")
              .append("<strong>Alerte seuil</strong><br>Ton solde descendra a <strong>")
              .append(fmt(alerte.getSoldeMinimum())).append("</strong> le ")
              .append(alerte.getDateMinimum().format(FR))
              .append(", sous ton seuil de ").append(fmt(alerte.getSeuil())).append(".</div>")
              .append("</td></tr>");
        }

        // Mouvements a venir
        sb.append("<tr><td style=\"padding-top:24px;\">")
          .append("<h2 style=\"font-size:17px;font-weight:800;margin:0 0 12px;color:")
          .append(ENCRE).append(";\">D ici la semaine prochaine</h2>");

        if (projection.getMouvements().isEmpty()) {
            sb.append("<p style=\"color:").append(ENCRE_DOUCE)
              .append(";font-style:italic;font-size:14px;margin:0;\">")
              .append("Aucun mouvement prevu cette semaine.</p>");
        } else {
            sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
            for (MouvementDto m : projection.getMouvements()) {
                String couleur = m.getType() == TypeOperation.CREDIT ? CREDIT : DEBIT;
                String signe = m.getType() == TypeOperation.CREDIT ? "+" : "−";
                sb.append("<tr><td style=\"padding:12px 0;border-bottom:1px solid ").append(FILET)
                  .append(";\"><table role=\"presentation\" width=\"100%\"><tr>")
                  .append("<td width=\"70\" style=\"font-family:").append(POLICE_MONO)
                  .append(";font-size:13px;color:").append(ENCRE_DOUCE).append(";vertical-align:middle;\">")
                  .append(m.getDate().format(JOUR_COURT)).append("</td>")
                  .append("<td width=\"3\" style=\"background:").append(couleur)
                  .append(";padding:0;\">&nbsp;</td>")
                  .append("<td style=\"padding-left:14px;font-size:14px;vertical-align:middle;color:")
                  .append(ENCRE).append(";\">").append(escape(m.getLibelle())).append("</td>")
                  .append("<td align=\"right\" style=\"font-family:").append(POLICE_MONO)
                  .append(";color:").append(couleur).append(";font-size:14px;white-space:nowrap;")
                  .append("vertical-align:middle;\">").append(signe).append(" ")
                  .append(fmt(m.getMontant())).append("</td>")
                  .append("</tr></table></td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style=\"padding-top:32px;text-align:center;font-size:12px;color:")
          .append(ENCRE_DOUCE)
          .append(";\">Envoye par tlprice · <span style=\"color:").append(CREDIT).append(";\">·</span>")
          .append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String fmt(BigDecimal montant) {
        if (montant == null) return "-";
        return String.format(Locale.FRENCH, "%,.2f €", montant);
    }
}
