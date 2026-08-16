package fr.tlprice.services;

import fr.tlprice.dto.AlerteSoldeDto;
import fr.tlprice.dto.JourCalendrierDto;
import fr.tlprice.dto.CapaciteEpargneDto;
import fr.tlprice.dto.MouvementDto;
import fr.tlprice.dto.ParametresDto;
import fr.tlprice.dto.SoldeProjeteDto;
import fr.tlprice.entities.Parametres;
import fr.tlprice.repositories.ParametresRepository;
import fr.tlprice.entities.*;
import fr.tlprice.entities.LigneScenario;
import fr.tlprice.entities.Scenario;
import fr.tlprice.repositories.CompteRepository;
import fr.tlprice.repositories.ScenarioRepository;
import fr.tlprice.repositories.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * solde projete (a la date D) =
 *      solde reel (banque)
 *    + credits des echeances entre dateMaj+1 et D
 *    - debits des echeances entre dateMaj+1 et D
 *    +/- transactions ponctuelles non encore debitees en banque
 *
 * C est ce qui compense le delai d affichage du solde par la banque.
 */
@Service
public class SoldeService {

    private final CompteRepository compteRepository;
    private final TransactionRepository transactionRepository;
    private final EcheanceService echeanceService;
    private final ParametresRepository parametresRepository;

    private final ScenarioRepository scenarioRepository;

    public SoldeService(CompteRepository compteRepository,
                        ScenarioRepository scenarioRepository,
                        TransactionRepository transactionRepository,
                        EcheanceService echeanceService,
                        ParametresRepository parametresRepository) {
        this.compteRepository = compteRepository;
        this.scenarioRepository = scenarioRepository;
        this.transactionRepository = transactionRepository;
        this.echeanceService = echeanceService;
        this.parametresRepository = parametresRepository;
    }

    /** Recupere le compte de l utilisateur, le cree a 0 s il n existe pas. */
    @Transactional
    public Compte recupererCompte(Utilisateur utilisateur) {
        return compteRepository.findByUtilisateur(utilisateur).orElseGet(() -> {
            Compte compte = new Compte();
            compte.setUtilisateur(utilisateur);
            compte.setSolde(BigDecimal.ZERO);
            compte.setDateMaj(LocalDate.now());
            return compteRepository.save(compte);
        });
    }

    @Transactional
    public Compte mettreAJourSolde(Utilisateur utilisateur, BigDecimal nouveauSolde) {
        Compte compte = recupererCompte(utilisateur);
        compte.setSolde(nouveauSolde);
        compte.setDateMaj(LocalDate.now());
        return compteRepository.save(compte);
    }

    @Transactional
    public SoldeProjeteDto calculerSoldeProjete(Utilisateur utilisateur, LocalDate dateProjection) {
        return calculerSoldeProjete(utilisateur, dateProjection, null);
    }

    /**
     * Meme calcul, augmente des lignes d un scenario.
     *
     * Les hypotheses ne sont jamais persistees comme des mouvements reels :
     * elles sont injectees ici, a la volee, et disparaissent des qu on
     * deselectionne le scenario. La projection reelle n est donc jamais
     * polluee, ce qui compte quand le chiffre affiche sert a decider d un
     * achat.
     *
     * @param scenarioId hypothese a appliquer, ou null pour la projection reelle
     */
    @Transactional(readOnly = true)
    public SoldeProjeteDto calculerSoldeProjete(Utilisateur utilisateur,
                                                LocalDate dateProjection,
                                                Long scenarioId) {
        Compte compte = recupererCompte(utilisateur);
        LocalDate debut = compte.getDateMaj().plusDays(1);

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<MouvementDto> mouvements = new ArrayList<>();

        // 1. Echeances des operations recurrentes entre la maj du solde et la date cible
        if (!debut.isAfter(dateProjection)) {
            mouvements.addAll(echeanceService.genererEcheances(utilisateur, debut, dateProjection));
        }

        // 2. Transactions ponctuelles pas encore visibles sur le solde banque
        for (Transaction t : transactionRepository.findByUtilisateurAndDebiteeEnBanqueFalse(utilisateur)) {
            if (!t.getDateOperation().isAfter(dateProjection)) {
                mouvements.add(new MouvementDto(t.getDateOperation(), t.getLibelle(),
                    t.getMontant(), t.getType(), t.getCategorie(), "TRANSACTION", null, null));
            }
        }

        // 3. Lignes hypothetiques du scenario selectionne
        if (scenarioId != null) {
            Scenario scenario = scenarioRepository.findByIdAndUtilisateur(scenarioId, utilisateur)
                .orElseThrow(() -> new IllegalStateException("Scenario introuvable."));
            for (LigneScenario l : scenario.getLignes()) {
                if (l.getDate().isAfter(dateProjection)) continue;
                if (l.getDate().isBefore(debut)) continue;
                mouvements.add(new MouvementDto(l.getDate(), l.getLibelle(),
                    l.getMontant(), l.getType(), l.getCategorie(), "SCENARIO", null, null));
            }
        }

        mouvements.sort(Comparator.comparing(MouvementDto::getDate));

        for (MouvementDto m : mouvements) {
            if (m.getType() == TypeOperation.DEBIT) {
                totalDebits = totalDebits.add(m.getMontant());
            } else {
                totalCredits = totalCredits.add(m.getMontant());
            }
        }

        BigDecimal soldeProjete = compte.getSolde().add(totalCredits).subtract(totalDebits);
        return new SoldeProjeteDto(compte.getSolde(), compte.getDateMaj(), dateProjection,
            totalDebits, totalCredits, soldeProjete, mouvements);
    }


    /**
     * Calendrier mensuel : pour chaque jour du mois, ses mouvements et le
     * solde projete en fin de journee. Passe unique sur les mouvements.
     */
    @Transactional(readOnly = true)
    public List<JourCalendrierDto> calendrierMensuel(Utilisateur utilisateur, YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        SoldeProjeteDto projection = calculerSoldeProjete(utilisateur, fin);
        Compte compte = recupererCompte(utilisateur);

        java.math.BigDecimal solde = compte.getSolde();
        // Appliquer les mouvements anterieurs au mois affiche
        List<MouvementDto> tous = projection.getMouvements();
        int i = 0;
        while (i < tous.size() && tous.get(i).getDate().isBefore(debut)) {
            solde = appliquer(solde, tous.get(i));
            i++;
        }

        List<JourCalendrierDto> jours = new ArrayList<>();
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            List<MouvementDto> jour = new ArrayList<>();
            while (i < tous.size() && tous.get(i).getDate().equals(d)) {
                solde = appliquer(solde, tous.get(i));
                jour.add(tous.get(i));
                i++;
            }
            jours.add(new JourCalendrierDto(d, jour, solde));
        }
        return jours;
    }

    private java.math.BigDecimal appliquer(java.math.BigDecimal solde, MouvementDto m) {
        return m.getType() == TypeOperation.CREDIT
            ? solde.add(m.getMontant())
            : solde.subtract(m.getMontant());
    }

    /**
     * Verifie si le solde projete tombe sous le seuil au cours des 30
     * prochains jours. Renvoie null-alerte si pas de seuil configure.
     */
    @Transactional(readOnly = true)
    public AlerteSoldeDto verifierSeuil(Utilisateur utilisateur) {
        Optional<Parametres> params = parametresRepository.findByUtilisateur(utilisateur);
        java.math.BigDecimal seuil = params.map(Parametres::getSeuilAlerte).orElse(null);
        LocalDate aujourdhui = LocalDate.now();
        LocalDate fin = aujourdhui.plusDays(30);
        SoldeProjeteDto projection = calculerSoldeProjete(utilisateur, fin);

        java.math.BigDecimal solde = projection.getSoldeReel();
        // Appliquer les mouvements deja passes
        for (MouvementDto m : projection.getMouvements()) {
            if (m.getDate().isBefore(aujourdhui)) {
                solde = appliquer(solde, m);
            }
        }
        java.math.BigDecimal min = solde;
        LocalDate dateMin = aujourdhui;
        for (MouvementDto m : projection.getMouvements()) {
            if (m.getDate().isBefore(aujourdhui)) continue;
            solde = appliquer(solde, m);
            if (solde.compareTo(min) < 0) {
                min = solde;
                dateMin = m.getDate();
            }
        }
        boolean alerte = seuil != null && min.compareTo(seuil) < 0;
        return new AlerteSoldeDto(seuil, min, dateMin, alerte);
    }

    /**
     * Combien on peut virer ailleurs sans passer sous son seuil de securite.
     *
     * Le raisonnement est simple et c est ce qui le rend fiable : on parcourt
     * la periode, on retient le solde le plus bas atteint, et on retranche le
     * seuil. Retirer davantage ferait forcement passer ce creux en dessous —
     * peu importe qu on soit large le reste du temps.
     *
     * Sans seuil configure, on prend zero : la reponse devient « combien sans
     * tomber a decouvert », ce qui reste la bonne question.
     *
     * Le resultat n est jamais negatif : « tu peux virer -120 euros » ne veut
     * rien dire. Zero signifie qu il n y a pas de marge, et le creux affiche a
     * cote dit a quel point.
     */
    @Transactional(readOnly = true)
    public CapaciteEpargneDto capaciteEpargne(Utilisateur utilisateur, LocalDate horizon) {
        Optional<Parametres> params = parametresRepository.findByUtilisateur(utilisateur);
        java.math.BigDecimal seuil = params.map(Parametres::getSeuilAlerte).orElse(null);
        boolean seuilConfigure = seuil != null;
        if (seuil == null) seuil = java.math.BigDecimal.ZERO;

        LocalDate aujourdhui = LocalDate.now();
        if (horizon == null || horizon.isBefore(aujourdhui)) horizon = aujourdhui.plusDays(30);

        SoldeProjeteDto projection = calculerSoldeProjete(utilisateur, horizon);

        java.math.BigDecimal solde = projection.getSoldeReel();
        // Les mouvements deja passes sont appliques sans compter dans le creux :
        // ce qui est derriere nous ne contraint plus le virement.
        for (MouvementDto m : projection.getMouvements()) {
            if (m.getDate().isBefore(aujourdhui)) solde = appliquer(solde, m);
        }

        java.math.BigDecimal creux = solde;
        LocalDate dateCreux = aujourdhui;
        for (MouvementDto m : projection.getMouvements()) {
            if (m.getDate().isBefore(aujourdhui)) continue;
            solde = appliquer(solde, m);
            if (solde.compareTo(creux) < 0) {
                creux = solde;
                dateCreux = m.getDate();
            }
        }

        java.math.BigDecimal capacite = creux.subtract(seuil);
        if (capacite.signum() < 0) capacite = java.math.BigDecimal.ZERO;

        return new CapaciteEpargneDto(capacite, creux, dateCreux, seuil, horizon, seuilConfigure);
    }

    /** Recupere ou cree les parametres de l utilisateur. */
    @Transactional
    public Parametres parametres(Utilisateur utilisateur) {
        return parametresRepository.findByUtilisateur(utilisateur).orElseGet(() -> {
            Parametres p = new Parametres();
            p.setUtilisateur(utilisateur);
            p.setEmail(utilisateur.getEmail());
            return parametresRepository.save(p);
        });
    }

    @Transactional
    public Parametres majParametres(Utilisateur utilisateur, ParametresDto dto) {
        Parametres p = parametres(utilisateur);
        p.setEmail(dto.getEmail() == null || dto.getEmail().isBlank()
            ? utilisateur.getEmail() : dto.getEmail().toLowerCase().trim());
        p.setAlertesEmail(dto.isAlertesEmail());
        p.setSeuilAlerte(dto.getSeuilAlerte());
        p.setHeureRecap(dto.getHeureRecap());
        p.setPushActif(dto.isPushActif());
        p.setHeureRecapPush(dto.getHeureRecapPush());
        p.setPushSiVide(dto.isPushSiVide());
        return parametresRepository.save(p);
    }

}
