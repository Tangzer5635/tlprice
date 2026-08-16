package fr.tlprice.services;

import fr.tlprice.dto.FiabiliteDto;
import fr.tlprice.dto.PointHistoriqueDto;
import fr.tlprice.dto.SoldeProjeteDto;
import fr.tlprice.entities.Compte;
import fr.tlprice.entities.HistoriqueSolde;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.CompteRepository;
import fr.tlprice.repositories.HistoriqueSoldeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Memoire du solde jour par jour.
 *
 * La capture se declenche a deux moments : automatiquement chaque soir,
 * et a chaque fois que le solde bouge (synchro bancaire ou correction
 * manuelle). Le point du jour est ecrase plutot que duplique, donc
 * plusieurs synchros dans la journee ne creent qu une ligne — celle qui
 * reflete l etat le plus recent.
 */
@Service
public class HistoriqueSoldeService {

    private static final Logger log = LoggerFactory.getLogger(HistoriqueSoldeService.class);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** Horizon de la projection archivee, en jours. */
    private static final int HORIZON = 30;

    /** Au-dela, on purge : deux ans de points suffisent largement. */
    private static final int RETENTION_JOURS = 730;

    private final HistoriqueSoldeRepository historiqueRepository;
    private final CompteRepository compteRepository;
    private final SoldeService soldeService;

    public HistoriqueSoldeService(HistoriqueSoldeRepository historiqueRepository,
                                  CompteRepository compteRepository,
                                  SoldeService soldeService) {
        this.historiqueRepository = historiqueRepository;
        this.compteRepository = compteRepository;
        this.soldeService = soldeService;
    }

    // =========================================================================
    // Capture
    // =========================================================================

    /**
     * Enregistre (ou met a jour) le point du jour. Silencieux en cas
     * d echec : une capture ratee ne doit jamais faire echouer la synchro
     * ou la correction de solde qui l a declenchee.
     */
    @Transactional
    public void capturer(Utilisateur utilisateur) {
        try {
            LocalDate aujourdhui = LocalDate.now(PARIS);
            Compte compte = soldeService.recupererCompte(utilisateur);
            SoldeProjeteDto projection =
                soldeService.calculerSoldeProjete(utilisateur, aujourdhui.plusDays(HORIZON));

            HistoriqueSolde point = historiqueRepository
                .findByUtilisateurAndDate(utilisateur, aujourdhui)
                .orElseGet(() -> {
                    HistoriqueSolde h = new HistoriqueSolde();
                    h.setUtilisateur(utilisateur);
                    h.setDate(aujourdhui);
                    return h;
                });

            point.setSoldeReel(compte.getSolde());
            point.setSoldeProjete(projection.getSoldeProjete());
            point.setDateProjection(aujourdhui.plusDays(HORIZON));
            point.setFraicheurJours(
                (int) ChronoUnit.DAYS.between(compte.getDateMaj(), aujourdhui));
            historiqueRepository.save(point);
        } catch (Exception e) {
            log.warn("Capture d historique echouee pour {} : {}",
                utilisateur.getEmail(), e.getMessage());
        }
    }

    /**
     * Filet de securite : meme sans synchro ni correction, on veut un point
     * par jour. Sinon la courbe du passe aurait des trous les jours ou l app
     * n a pas ete ouverte, alors que la projection, elle, a evolue.
     */
    @Scheduled(cron = "0 45 23 * * *", zone = "Europe/Paris")
    public void capturerQuotidiennement() {
        for (Compte compte : compteRepository.findAll()) {
            capturer(compte.getUtilisateur());
        }
        purger();
    }

    @Transactional
    public void purger() {
        LocalDate limite = LocalDate.now(PARIS).minusDays(RETENTION_JOURS);
        for (Compte compte : compteRepository.findAll()) {
            historiqueRepository.deleteByUtilisateurAndDateBefore(compte.getUtilisateur(), limite);
        }
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    @Transactional(readOnly = true)
    public List<PointHistoriqueDto> lire(Utilisateur utilisateur, int jours) {
        LocalDate depuis = LocalDate.now(PARIS).minusDays(Math.min(jours, RETENTION_JOURS));
        List<PointHistoriqueDto> points = new ArrayList<>();
        for (HistoriqueSolde h : historiqueRepository
                .findByUtilisateurAndDateGreaterThanEqualOrderByDateAsc(utilisateur, depuis)) {
            points.add(new PointHistoriqueDto(h.getDate(), h.getSoldeReel(), h.getFraicheurJours()));
        }
        return points;
    }

    /**
     * La projection faite il y a 30 jours annoncait X pour aujourd hui ;
     * le solde constate est Y. C est la seule mesure honnete de la qualite
     * des previsions.
     */
    @Transactional(readOnly = true)
    public FiabiliteDto fiabilite(Utilisateur utilisateur) {
        LocalDate aujourdhui = LocalDate.now(PARIS);
        List<HistoriqueSolde> tout =
            historiqueRepository.findByUtilisateurOrderByDateAsc(utilisateur);
        if (tout.isEmpty()) {
            return new FiabiliteDto(null, null, null, null, 0);
        }

        int joursHistorique = (int) ChronoUnit.DAYS
            .between(tout.get(0).getDate(), aujourdhui) + 1;

        Optional<HistoriqueSolde> aujourdhuiPoint = historiqueRepository
            .findByUtilisateurAndDate(utilisateur, aujourdhui);
        if (aujourdhuiPoint.isEmpty()) {
            return new FiabiliteDto(null, null, null, null, joursHistorique);
        }
        BigDecimal constate = aujourdhuiPoint.get().getSoldeReel();

        // Le point dont la projection visait aujourd hui, a quelques jours
        // pres : on prend le plus proche, l historique pouvant avoir des trous.
        HistoriqueSolde meilleur = null;
        long meilleurEcart = Long.MAX_VALUE;
        for (HistoriqueSolde h : tout) {
            if (h.getDateProjection() == null || h.getSoldeProjete() == null) continue;
            long ecart = Math.abs(ChronoUnit.DAYS.between(h.getDateProjection(), aujourdhui));
            if (ecart <= 3 && ecart < meilleurEcart) {
                meilleur = h;
                meilleurEcart = ecart;
            }
        }
        if (meilleur == null) {
            return new FiabiliteDto(null, null, constate, null, joursHistorique);
        }

        BigDecimal annonce = meilleur.getSoldeProjete();
        return new FiabiliteDto(meilleur.getDate(), annonce, constate,
            constate.subtract(annonce), joursHistorique);
    }
}
