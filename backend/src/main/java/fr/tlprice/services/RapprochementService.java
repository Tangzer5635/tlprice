package fr.tlprice.services;

import fr.tlprice.dto.MouvementDto;
import fr.tlprice.dto.RapprochementDto;
import fr.tlprice.dto.TransactionBancaireDto;
import fr.tlprice.entities.*;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import fr.tlprice.repositories.RapprochementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Confronte les echeances annoncees par tlprice a ce que la banque a
 * reellement debite.
 *
 * Le probleme central est l appariement : une echeance s appelle "Loyer",
 * la ligne bancaire "PRLV SEPA SCI DES TILLEULS 2024". Aucun token commun.
 * On s appuie donc sur les regles de renommage deja apprises par
 * l utilisateur dans l ecran Import : si une regle transforme cette ligne
 * en "Loyer", le rapprochement devient evident. Plus l utilisateur nomme
 * ses lignes, plus le controle devient fiable — le meme travail sert deux
 * fois.
 *
 * A defaut de libelle exploitable, on retombe sur un appariement par
 * montant et date, marque PROBABLE : l utilisateur tranche.
 */
@Service
public class RapprochementService {

    private static final Logger log = LoggerFactory.getLogger(RapprochementService.class);

    /** Tolerance de date entre l echeance prevue et le debit reel. */
    private static final int JOURS_TOLERANCE = 5;

    /** En dessous, on considere que les montants sont identiques. */
    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    /**
     * Une echeance n est declaree manquante qu apres ce delai : une banque
     * met parfois deux jours a comptabiliser un prelevement.
     */
    private static final int JOURS_GRACE = 3;

    /** Ecart de montant tolere pour un appariement au montant seul (5%). */
    private static final BigDecimal RATIO_PROBABLE = new BigDecimal("0.05");

    private final RapprochementRepository rapprochementRepository;
    private final OperationRecurrenteRepository operationRepository;
    private final ExceptionEcheanceRepository exceptionRepository;
    private final EcheanceService echeanceService;
    private final EnableBankingService enableBankingService;
    private final RegleRenommageService reglesService;

    public RapprochementService(RapprochementRepository rapprochementRepository,
                                OperationRecurrenteRepository operationRepository,
                                ExceptionEcheanceRepository exceptionRepository,
                                EcheanceService echeanceService,
                                EnableBankingService enableBankingService,
                                RegleRenommageService reglesService) {
        this.rapprochementRepository = rapprochementRepository;
        this.operationRepository = operationRepository;
        this.exceptionRepository = exceptionRepository;
        this.echeanceService = echeanceService;
        this.enableBankingService = enableBankingService;
        this.reglesService = reglesService;
    }

    // =========================================================================
    // Analyse
    // =========================================================================

    /**
     * Rejoue l analyse sur les N derniers jours et met la table a jour.
     * Idempotent : relancer ne cree pas de doublon et ne rouvre pas ce que
     * l utilisateur a deja traite, sauf si le montant reel a change entre
     * deux passages.
     */
    @Transactional
    public int analyser(Utilisateur utilisateur, int jours) {
        LocalDate fin = LocalDate.now();
        LocalDate debut = fin.minusDays(Math.min(jours, 90));

        List<TransactionBancaireDto> lignes =
            enableBankingService.listerTransactionsBancaires(utilisateur, Math.min(jours, 90));
        List<MouvementDto> echeances =
            echeanceService.genererEcheances(utilisateur, debut, fin);

        // Une ligne bancaire ne peut servir qu a une seule echeance.
        Set<String> consommees = new HashSet<>();
        int nouveautes = 0;

        for (MouvementDto echeance : echeances) {
            if (echeance.getOperationId() == null) continue;
            Optional<OperationRecurrente> operation =
                operationRepository.findByIdAndUtilisateur(echeance.getOperationId(), utilisateur);
            if (operation.isEmpty()) continue;

            Correspondance trouvee = apparier(utilisateur, echeance, lignes, consommees);
            if (trouvee != null && trouvee.ligne.getReference() != null) {
                consommees.add(trouvee.ligne.getReference());
            }

            if (enregistrer(utilisateur, operation.get(), echeance, trouvee, fin)) {
                nouveautes++;
            }
        }
        return nouveautes;
    }

    /** Le resultat d un appariement : la ligne bancaire et sa fiabilite. */
    private static final class Correspondance {
        final TransactionBancaireDto ligne;
        final ConfianceRapprochement confiance;
        Correspondance(TransactionBancaireDto ligne, ConfianceRapprochement confiance) {
            this.ligne = ligne;
            this.confiance = confiance;
        }
    }

    /**
     * Cherche la ligne bancaire correspondant a une echeance. Deux passes :
     * d abord sur le libelle (fiable), puis sur montant + date (probable).
     */
    private Correspondance apparier(Utilisateur utilisateur, MouvementDto echeance,
                                    List<TransactionBancaireDto> lignes,
                                    Set<String> consommees) {
        String attendu = echeance.getLibelle();
        TransactionBancaireDto meilleurCertain = null;
        TransactionBancaireDto meilleurProbable = null;
        BigDecimal meilleurEcartProbable = null;

        for (TransactionBancaireDto ligne : lignes) {
            if (ligne.getReference() != null && consommees.contains(ligne.getReference())) continue;
            if (!memeSens(echeance, ligne)) continue;

            LocalDate dateLigne = parseDate(ligne.getDate());
            if (dateLigne == null) continue;
            long ecartJours = Math.abs(
                java.time.temporal.ChronoUnit.DAYS.between(echeance.getDate(), dateLigne));
            if (ecartJours > JOURS_TOLERANCE) continue;

            if (librellesCorrespondent(utilisateur, attendu, ligne)) {
                // Entre deux candidats au libelle correct, on garde celui
                // dont le montant colle le mieux.
                if (meilleurCertain == null
                    || ecartAbsolu(echeance, ligne)
                        .compareTo(ecartAbsolu(echeance, meilleurCertain)) < 0) {
                    meilleurCertain = ligne;
                }
                continue;
            }

            BigDecimal ecart = ecartAbsolu(echeance, ligne);
            BigDecimal tolere = echeance.getMontant().multiply(RATIO_PROBABLE);
            if (ecart.compareTo(tolere) <= 0
                && (meilleurEcartProbable == null || ecart.compareTo(meilleurEcartProbable) < 0)) {
                meilleurProbable = ligne;
                meilleurEcartProbable = ecart;
            }
        }

        if (meilleurCertain != null) {
            return new Correspondance(meilleurCertain, ConfianceRapprochement.CERTAIN);
        }
        if (meilleurProbable != null) {
            return new Correspondance(meilleurProbable, ConfianceRapprochement.PROBABLE);
        }
        return null;
    }

    /**
     * Le libelle de l echeance et celui de la banque designent-ils la meme
     * chose ? On tente d abord le libelle brut, puis le libelle reecrit par
     * une regle de renommage apprise dans l ecran Import.
     */
    private boolean librellesCorrespondent(Utilisateur utilisateur, String attendu,
                                           TransactionBancaireDto ligne) {
        String motif = NormalisationLibelle.motifDe(attendu);
        if (motif.isBlank()) return false;
        if (NormalisationLibelle.matche(motif, ligne.getLibelle())) return true;

        String reecrit = ligne.getLibelleAuto();
        if (reecrit == null) {
            reecrit = reglesService.appliquer(utilisateur, ligne.getLibelle())
                .map(RegleRenommage::getLibelleFinal)
                .orElse(null);
        }
        return reecrit != null && NormalisationLibelle.matche(motif, reecrit);
    }

    private boolean memeSens(MouvementDto echeance, TransactionBancaireDto ligne) {
        String sensEcheance = echeance.getType() == TypeOperation.CREDIT ? "CREDIT" : "DEBIT";
        return sensEcheance.equals(ligne.getType());
    }

    private BigDecimal ecartAbsolu(MouvementDto echeance, TransactionBancaireDto ligne) {
        return ligne.getMontant().subtract(echeance.getMontant()).abs();
    }

    private LocalDate parseDate(String iso) {
        try {
            return iso == null || iso.isBlank() ? null : LocalDate.parse(iso.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cree ou met a jour la ligne de rapprochement. Renvoie vrai s il s agit
     * d une nouveaute a signaler a l utilisateur.
     */
    private boolean enregistrer(Utilisateur utilisateur, OperationRecurrente operation,
                                MouvementDto echeance, Correspondance trouvee,
                                LocalDate aujourdhui) {
        StatutRapprochement statut;
        BigDecimal montantReel = null;
        LocalDate dateBancaire = null;
        String libelleBancaire = null;
        String reference = null;
        ConfianceRapprochement confiance = ConfianceRapprochement.AUCUNE;

        if (trouvee != null) {
            montantReel = trouvee.ligne.getMontant();
            dateBancaire = parseDate(trouvee.ligne.getDate());
            libelleBancaire = tronquer(trouvee.ligne.getLibelle(), 300);
            reference = tronquer(trouvee.ligne.getReference(), 200);
            confiance = trouvee.confiance;
            statut = montantReel.subtract(echeance.getMontant()).abs().compareTo(EPSILON) <= 0
                ? StatutRapprochement.CONFIRME
                : StatutRapprochement.ECART;
        } else {
            // Trop recent pour conclure : la banque n a peut-etre pas encore
            // comptabilise. On ne cree pas de ligne du tout.
            if (!echeance.getDate().isBefore(aujourdhui.minusDays(JOURS_GRACE))) {
                return false;
            }
            statut = StatutRapprochement.MANQUANT;
        }

        Optional<RapprochementEcheance> existant =
            rapprochementRepository.findByOperationAndDateEcheance(operation, echeance.getDate());

        RapprochementEcheance ligne = existant.orElseGet(() -> {
            RapprochementEcheance r = new RapprochementEcheance();
            r.setUtilisateur(utilisateur);
            r.setOperation(operation);
            r.setDateEcheance(echeance.getDate());
            return r;
        });

        // Si le montant reel a change depuis le dernier passage, le verdict
        // de l utilisateur ne vaut plus : on rouvre la ligne. Mais on ne la
        // rouvre jamais pour annoncer que tout va bien — sinon corriger un
        // ecart le ferait reapparaitre au passage suivant, une fois devenu
        // conforme, ce qui serait absurde.
        boolean montantChange = ligne.getMontantReel() != null && montantReel != null
            && ligne.getMontantReel().compareTo(montantReel) != 0;
        boolean statutChange = ligne.getStatut() != null && ligne.getStatut() != statut;
        boolean resteUnProbleme = statut != StatutRapprochement.CONFIRME;
        if ((montantChange || statutChange) && resteUnProbleme) {
            ligne.setTraite(false);
        }

        boolean nouveau = existant.isEmpty();
        ligne.setMontantPrevu(echeance.getMontant());
        ligne.setMontantReel(montantReel);
        ligne.setDateBancaire(dateBancaire);
        ligne.setLibelleBancaire(libelleBancaire);
        ligne.setReferenceBancaire(reference);
        ligne.setStatut(statut);
        ligne.setConfiance(confiance);
        rapprochementRepository.save(ligne);

        return (nouveau || montantChange || statutChange)
            && statut != StatutRapprochement.CONFIRME;
    }

    private static String tronquer(String texte, int max) {
        if (texte == null) return null;
        return texte.length() <= max ? texte : texte.substring(0, max);
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    private static final List<StatutRapprochement> A_TRAITER =
        List.of(StatutRapprochement.ECART, StatutRapprochement.MANQUANT);

    @Transactional(readOnly = true)
    public List<RapprochementDto> lister(Utilisateur utilisateur, boolean tout) {
        List<RapprochementEcheance> lignes = tout
            ? rapprochementRepository.findByUtilisateurOrderByDateEcheanceDesc(utilisateur)
            : rapprochementRepository
                .findByUtilisateurAndTraiteFalseAndStatutInOrderByDateEcheanceDesc(
                    utilisateur, A_TRAITER);

        List<RapprochementDto> sortie = new ArrayList<>();
        for (RapprochementEcheance r : lignes) {
            BigDecimal ecart = r.getMontantReel() == null
                ? null : r.getMontantReel().subtract(r.getMontantPrevu());
            sortie.add(new RapprochementDto(
                r.getId(),
                r.getOperation().getId(),
                r.getOperation().getLibelle(),
                r.getDateEcheance(),
                r.getDateBancaire(),
                r.getMontantPrevu(),
                r.getMontantReel(),
                ecart,
                r.getLibelleBancaire(),
                r.getStatut(),
                r.getConfiance(),
                r.isTraite()));
        }
        return sortie;
    }

    /** Compteur pour la pastille du tableau de bord. */
    @Transactional(readOnly = true)
    public long nombreEnAttente(Utilisateur utilisateur) {
        return rapprochementRepository
            .countByUtilisateurAndTraiteFalseAndStatutIn(utilisateur, A_TRAITER);
    }

    // =========================================================================
    // Actions de l utilisateur
    // =========================================================================

    private RapprochementEcheance obligatoire(Long id, Utilisateur utilisateur) {
        return rapprochementRepository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Rapprochement introuvable"));
    }

    /**
     * Ce mois-ci seulement : cree une exception d echeance a la date
     * concernee, sans toucher au montant par defaut de l operation.
     */
    @Transactional
    public String corrigerPonctuellement(Utilisateur utilisateur, Long id) {
        RapprochementEcheance r = obligatoire(id, utilisateur);
        if (r.getMontantReel() == null) {
            throw new IllegalStateException("Aucun montant reel a reporter.");
        }
        ExceptionEcheance ex = exceptionRepository
            .findByOperationAndDate(r.getOperation(), r.getDateEcheance())
            .orElseGet(() -> {
                ExceptionEcheance e = new ExceptionEcheance();
                e.setOperation(r.getOperation());
                e.setDate(r.getDateEcheance());
                return e;
            });
        ex.setMontantOverride(r.getMontantReel());
        ex.setAnnulee(false);
        exceptionRepository.save(ex);
        r.setTraite(true);
        rapprochementRepository.save(r);
        return apprendreDeLaConfirmation(utilisateur, r);
    }

    /**
     * A partir de maintenant : le montant reel devient le montant de
     * reference de l operation recurrente, donc toutes les projections
     * futures s ajustent.
     */
    @Transactional
    public String corrigerDefinitivement(Utilisateur utilisateur, Long id) {
        RapprochementEcheance r = obligatoire(id, utilisateur);
        if (r.getMontantReel() == null) {
            throw new IllegalStateException("Aucun montant reel a reporter.");
        }
        OperationRecurrente operation = r.getOperation();
        operation.setMontant(r.getMontantReel());
        operationRepository.save(operation);
        r.setTraite(true);
        rapprochementRepository.save(r);
        log.info("Montant de l operation {} passe a {}", operation.getId(), r.getMontantReel());
        return apprendreDeLaConfirmation(utilisateur, r);
    }

    /**
     * Le prelevement n a pas eu lieu : on annule cette echeance-la pour
     * qu elle cesse de peser sur les projections passees et le calendrier.
     */
    @Transactional
    public void annulerEcheance(Utilisateur utilisateur, Long id) {
        RapprochementEcheance r = obligatoire(id, utilisateur);
        ExceptionEcheance ex = exceptionRepository
            .findByOperationAndDate(r.getOperation(), r.getDateEcheance())
            .orElseGet(() -> {
                ExceptionEcheance e = new ExceptionEcheance();
                e.setOperation(r.getOperation());
                e.setDate(r.getDateEcheance());
                return e;
            });
        ex.setAnnulee(true);
        exceptionRepository.save(ex);
        r.setTraite(true);
        rapprochementRepository.save(r);
    }

    // =========================================================================
    // Apprentissage
    // =========================================================================

    /**
     * Quand l utilisateur valide une correction sur une ligne appariee en
     * PROBABLE, il vient de nous apprendre quelque chose que la machine ne
     * pouvait pas deviner : que "PRLV SEPA SCI DES TILLEULS 2024" designe
     * bien son "Loyer". L information etait la, on ne la gardait pas.
     *
     * On cree donc la regle de renommage correspondante. L effet compose au
     * fil des mois : chaque confirmation transforme un appariement PROBABLE
     * en CERTAIN pour toutes les occurrences suivantes, et la meme regle sert
     * aussi a l ecran Import. Le systeme s ameliore par l usage au lieu de
     * reposer la meme question tous les mois.
     *
     * Trois garde-fous. On n apprend que sur les appariements PROBABLE : un
     * CERTAIN a deja matche par le libelle, il n a rien a apprendre. On
     * n ecrase jamais une regle qui couvre deja cette ligne, sinon un mauvais
     * appariement pourrait defaire un renommage correct. Et un echec
     * d apprentissage — libelle bancaire trop generique pour en tirer un
     * motif — ne fait jamais echouer la correction elle-meme : l utilisateur
     * a demande a corriger un montant, pas a creer une regle.
     *
     * @return le libelle de la regle creee, ou null si rien n a ete appris
     */
    private String apprendreDeLaConfirmation(Utilisateur utilisateur, RapprochementEcheance r) {
        if (r.getConfiance() != ConfianceRapprochement.PROBABLE) return null;

        String libelleBancaire = r.getLibelleBancaire();
        if (libelleBancaire == null || libelleBancaire.isBlank()) return null;

        // Deja couvert par une regle existante : ne pas la reecrire.
        if (reglesService.appliquer(utilisateur, libelleBancaire).isPresent()) return null;

        OperationRecurrente operation = r.getOperation();
        try {
            RegleRenommage regle = reglesService.apprendre(
                utilisateur, libelleBancaire, operation.getLibelle(), operation.getCategorie());
            log.info("Regle apprise depuis le controle : \"{}\" -> \"{}\"",
                regle.getMotif(), regle.getLibelleFinal());
            return regle.getLibelleFinal();
        } catch (RuntimeException e) {
            log.info("Aucune regle tiree du rapprochement {} : {}", r.getId(), e.getMessage());
            return null;
        }
    }

    /** Rien a corriger, on ne veut simplement plus voir la ligne. */
    @Transactional
    public void ignorer(Utilisateur utilisateur, Long id) {
        RapprochementEcheance r = obligatoire(id, utilisateur);
        r.setTraite(true);
        rapprochementRepository.save(r);
    }
}
