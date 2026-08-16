package fr.tlprice.services;

import fr.tlprice.dto.RapportImportDto;
import fr.tlprice.dto.SauvegardeDto;
import fr.tlprice.entities.Compte;
import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Parametres;
import fr.tlprice.entities.RegleRenommage;
import fr.tlprice.entities.Transaction;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.CompteRepository;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import fr.tlprice.repositories.ParametresRepository;
import fr.tlprice.repositories.RegleRenommageRepository;
import fr.tlprice.repositories.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transfert d un compte entre deux instances de tlprice : on exporte tout
 * ce qui a ete saisi a la main (operations recurrentes et leurs ajustements,
 * transactions ponctuelles, regles de renommage, preferences) dans un JSON,
 * qu on reimporte ailleurs.
 *
 * Rien n est jamais ecrase en mode FUSION : un element deja present est
 * simplement ignore, ce qui rend l import rejouable sans creer de doublons.
 */
@Service
public class SauvegardeService {

    public static final String FORMAT = "tlprice-sauvegarde";

    private final CompteRepository compteRepository;
    private final ParametresRepository parametresRepository;
    private final OperationRecurrenteRepository operationRepository;
    private final ExceptionEcheanceRepository exceptionRepository;
    private final TransactionRepository transactionRepository;
    private final RegleRenommageRepository regleRepository;

    public SauvegardeService(CompteRepository compteRepository,
                             ParametresRepository parametresRepository,
                             OperationRecurrenteRepository operationRepository,
                             ExceptionEcheanceRepository exceptionRepository,
                             TransactionRepository transactionRepository,
                             RegleRenommageRepository regleRepository) {
        this.compteRepository = compteRepository;
        this.parametresRepository = parametresRepository;
        this.operationRepository = operationRepository;
        this.exceptionRepository = exceptionRepository;
        this.transactionRepository = transactionRepository;
        this.regleRepository = regleRepository;
    }

    // ==================================================================
    // Export
    // ==================================================================

    @Transactional(readOnly = true)
    public SauvegardeDto exporter(Utilisateur utilisateur) {
        SauvegardeDto sauvegarde = new SauvegardeDto();
        sauvegarde.setFormat(FORMAT);
        sauvegarde.setVersion(1);
        sauvegarde.setDateExport(OffsetDateTime.now());
        sauvegarde.setCompteSource(utilisateur.getEmail());

        Compte compte = compteRepository.findByUtilisateur(utilisateur).orElse(null);
        if (compte != null) {
            sauvegarde.setSolde(compte.getSolde());
            sauvegarde.setSoldeDateMaj(compte.getDateMaj());
        }

        Parametres parametres = parametresRepository.findByUtilisateur(utilisateur).orElse(null);
        if (parametres != null) {
            SauvegardeDto.ParametresSauvegarde p = new SauvegardeDto.ParametresSauvegarde();
            p.setEmail(parametres.getEmail());
            p.setAlertesEmail(parametres.isAlertesEmail());
            p.setHeureRecap(parametres.getHeureRecap());
            p.setSeuilAlerte(parametres.getSeuilAlerte());
            sauvegarde.setParametres(p);
        }

        List<OperationRecurrente> operations =
            operationRepository.findByUtilisateurOrderByLibelleAsc(utilisateur);

        // Une seule requete pour toutes les exceptions, indexees par operation
        Map<Long, List<ExceptionEcheance>> exceptionsParOperation = new HashMap<>();
        if (!operations.isEmpty()) {
            for (ExceptionEcheance ex : exceptionRepository.findByOperationIn(operations)) {
                exceptionsParOperation
                    .computeIfAbsent(ex.getOperation().getId(), cle -> new ArrayList<>())
                    .add(ex);
            }
        }

        for (OperationRecurrente operation : operations) {
            SauvegardeDto.OperationSauvegarde o = new SauvegardeDto.OperationSauvegarde();
            o.setLibelle(operation.getLibelle());
            o.setMontant(operation.getMontant());
            o.setType(operation.getType());
            o.setFrequence(operation.getFrequence());
            o.setJourDuMois(operation.getJourDuMois());
            o.setCategorie(operation.getCategorie());
            o.setDateDebut(operation.getDateDebut());
            o.setDateFin(operation.getDateFin());
            o.setActive(operation.isActive());

            List<ExceptionEcheance> exceptions =
                exceptionsParOperation.getOrDefault(operation.getId(), List.of());
            for (ExceptionEcheance ex : exceptions) {
                SauvegardeDto.ExceptionSauvegarde e = new SauvegardeDto.ExceptionSauvegarde();
                e.setDate(ex.getDate());
                e.setMontantOverride(ex.getMontantOverride());
                e.setAnnulee(ex.isAnnulee());
                o.getExceptions().add(e);
            }
            sauvegarde.getOperations().add(o);
        }

        for (Transaction transaction :
                transactionRepository.findByUtilisateurOrderByDateOperationDesc(utilisateur)) {
            SauvegardeDto.TransactionSauvegarde t = new SauvegardeDto.TransactionSauvegarde();
            t.setLibelle(transaction.getLibelle());
            t.setMontant(transaction.getMontant());
            t.setType(transaction.getType());
            t.setCategorie(transaction.getCategorie());
            t.setDateOperation(transaction.getDateOperation());
            t.setDebiteeEnBanque(transaction.isDebiteeEnBanque());
            sauvegarde.getTransactions().add(t);
        }

        for (RegleRenommage regle :
                regleRepository.findByUtilisateurOrderByLibelleFinalAsc(utilisateur)) {
            SauvegardeDto.RegleSauvegarde r = new SauvegardeDto.RegleSauvegarde();
            r.setMotif(regle.getMotif());
            r.setLibelleFinal(regle.getLibelleFinal());
            r.setCategorie(regle.getCategorie());
            sauvegarde.getRegles().add(r);
        }

        return sauvegarde;
    }

    // ==================================================================
    // Import
    // ==================================================================

    /**
     * @param remplacer true = on efface d abord les donnees existantes,
     *                  false = fusion sans doublon
     */
    @Transactional
    public RapportImportDto importer(Utilisateur utilisateur, SauvegardeDto sauvegarde,
                                     boolean remplacer, boolean appliquerSolde,
                                     boolean appliquerParametres) {
        if (sauvegarde == null || !FORMAT.equals(sauvegarde.getFormat())) {
            throw new IllegalArgumentException(
                "Ce fichier n est pas une sauvegarde tlprice.");
        }
        if (sauvegarde.getVersion() > 1) {
            throw new IllegalArgumentException(
                "Sauvegarde creee par une version plus recente de tlprice (version "
                + sauvegarde.getVersion() + ").");
        }

        RapportImportDto rapport = new RapportImportDto();
        rapport.setMode(remplacer ? "remplacement" : "fusion");

        if (remplacer) {
            viderCompte(utilisateur);
        }

        importerOperations(utilisateur, sauvegarde, rapport);
        importerTransactions(utilisateur, sauvegarde, rapport);
        importerRegles(utilisateur, sauvegarde, rapport);

        if (appliquerSolde && sauvegarde.getSolde() != null) {
            Compte compte = compteRepository.findByUtilisateur(utilisateur).orElseGet(() -> {
                Compte nouveau = new Compte();
                nouveau.setUtilisateur(utilisateur);
                return nouveau;
            });
            compte.setSolde(sauvegarde.getSolde());
            compte.setDateMaj(sauvegarde.getSoldeDateMaj() != null
                ? sauvegarde.getSoldeDateMaj() : LocalDate.now());
            compteRepository.save(compte);
            rapport.setSoldeApplique(true);
        }

        if (appliquerParametres && sauvegarde.getParametres() != null) {
            SauvegardeDto.ParametresSauvegarde source = sauvegarde.getParametres();
            Parametres parametres = parametresRepository.findByUtilisateur(utilisateur)
                .orElseGet(() -> {
                    Parametres nouveau = new Parametres();
                    nouveau.setUtilisateur(utilisateur);
                    return nouveau;
                });
            parametres.setEmail(source.getEmail());
            parametres.setAlertesEmail(source.isAlertesEmail());
            parametres.setHeureRecap(source.getHeureRecap());
            parametres.setSeuilAlerte(source.getSeuilAlerte());
            parametresRepository.save(parametres);
            rapport.setParametresAppliques(true);
        }

        return rapport;
    }

    /** Efface operations, ajustements, transactions et regles de l utilisateur. */
    private void viderCompte(Utilisateur utilisateur) {
        // Les exceptions referencent les operations : elles partent en premier
        List<ExceptionEcheance> exceptions =
            exceptionRepository.findByOperation_Utilisateur(utilisateur);
        if (!exceptions.isEmpty()) {
            exceptionRepository.deleteAll(exceptions);
            exceptionRepository.flush();
        }
        operationRepository.deleteAll(
            operationRepository.findByUtilisateurOrderByLibelleAsc(utilisateur));
        transactionRepository.deleteAll(
            transactionRepository.findByUtilisateurOrderByDateOperationDesc(utilisateur));
        regleRepository.deleteAll(
            regleRepository.findByUtilisateurOrderByLibelleFinalAsc(utilisateur));
    }

    private void importerOperations(Utilisateur utilisateur, SauvegardeDto sauvegarde,
                                    RapportImportDto rapport) {
        Set<String> existantes = new HashSet<>();
        for (OperationRecurrente existante :
                operationRepository.findByUtilisateurOrderByLibelleAsc(utilisateur)) {
            existantes.add(cleOperation(existante.getLibelle(), existante.getMontant(),
                existante.getType() == null ? null : existante.getType().name(),
                existante.getFrequence() == null ? null : existante.getFrequence().name(),
                existante.getJourDuMois(), existante.getDateDebut()));
        }

        for (SauvegardeDto.OperationSauvegarde source : sauvegarde.getOperations()) {
            String cle = cleOperation(source.getLibelle(), source.getMontant(),
                source.getType() == null ? null : source.getType().name(),
                source.getFrequence() == null ? null : source.getFrequence().name(),
                source.getJourDuMois(), source.getDateDebut());
            if (!existantes.add(cle)) {
                rapport.setOperationsIgnorees(rapport.getOperationsIgnorees() + 1);
                continue;
            }

            OperationRecurrente operation = new OperationRecurrente();
            operation.setUtilisateur(utilisateur);
            operation.setLibelle(source.getLibelle());
            operation.setMontant(source.getMontant());
            operation.setType(source.getType());
            operation.setFrequence(source.getFrequence());
            operation.setJourDuMois(source.getJourDuMois());
            operation.setCategorie(source.getCategorie());
            operation.setDateDebut(source.getDateDebut());
            operation.setDateFin(source.getDateFin());
            operation.setActive(source.isActive());
            OperationRecurrente enregistree = operationRepository.save(operation);
            rapport.setOperationsAjoutees(rapport.getOperationsAjoutees() + 1);

            for (SauvegardeDto.ExceptionSauvegarde sourceEx : source.getExceptions()) {
                if (sourceEx.getDate() == null) continue;
                ExceptionEcheance exception = new ExceptionEcheance();
                exception.setOperation(enregistree);
                exception.setDate(sourceEx.getDate());
                exception.setMontantOverride(sourceEx.getMontantOverride());
                exception.setAnnulee(sourceEx.isAnnulee());
                exceptionRepository.save(exception);
                rapport.setExceptionsAjoutees(rapport.getExceptionsAjoutees() + 1);
            }
        }
    }

    private void importerTransactions(Utilisateur utilisateur, SauvegardeDto sauvegarde,
                                      RapportImportDto rapport) {
        Set<String> existantes = new HashSet<>();
        for (Transaction existante :
                transactionRepository.findByUtilisateurOrderByDateOperationDesc(utilisateur)) {
            existantes.add(cleTransaction(existante.getDateOperation(), existante.getLibelle(),
                existante.getMontant(),
                existante.getType() == null ? null : existante.getType().name()));
        }

        for (SauvegardeDto.TransactionSauvegarde source : sauvegarde.getTransactions()) {
            String cle = cleTransaction(source.getDateOperation(), source.getLibelle(),
                source.getMontant(), source.getType() == null ? null : source.getType().name());
            if (!existantes.add(cle)) {
                rapport.setTransactionsIgnorees(rapport.getTransactionsIgnorees() + 1);
                continue;
            }

            Transaction transaction = new Transaction();
            transaction.setUtilisateur(utilisateur);
            transaction.setLibelle(source.getLibelle());
            transaction.setMontant(source.getMontant());
            transaction.setType(source.getType());
            transaction.setCategorie(source.getCategorie());
            transaction.setDateOperation(source.getDateOperation());
            transaction.setDebiteeEnBanque(source.isDebiteeEnBanque());
            transactionRepository.save(transaction);
            rapport.setTransactionsAjoutees(rapport.getTransactionsAjoutees() + 1);
        }
    }

    private void importerRegles(Utilisateur utilisateur, SauvegardeDto sauvegarde,
                                RapportImportDto rapport) {
        Set<String> motifsExistants = new HashSet<>();
        for (RegleRenommage existante :
                regleRepository.findByUtilisateurOrderByLibelleFinalAsc(utilisateur)) {
            motifsExistants.add(existante.getMotif());
        }

        for (SauvegardeDto.RegleSauvegarde source : sauvegarde.getRegles()) {
            if (source.getMotif() == null || source.getMotif().isBlank()) continue;
            if (!motifsExistants.add(source.getMotif())) {
                rapport.setReglesIgnorees(rapport.getReglesIgnorees() + 1);
                continue;
            }
            RegleRenommage regle = new RegleRenommage();
            regle.setUtilisateur(utilisateur);
            regle.setMotif(source.getMotif());
            regle.setLibelleFinal(source.getLibelleFinal());
            regle.setCategorie(source.getCategorie());
            regleRepository.save(regle);
            rapport.setReglesAjoutees(rapport.getReglesAjoutees() + 1);
        }
    }

    // ------------------------------------------------------------------
    // Cles naturelles servant a detecter les doublons
    // ------------------------------------------------------------------

    private String cleOperation(String libelle, BigDecimal montant, String type,
                                String frequence, Integer jourDuMois, LocalDate dateDebut) {
        return normaliser(libelle) + "|" + montantNormalise(montant) + "|" + type
            + "|" + frequence + "|" + jourDuMois + "|" + dateDebut;
    }

    private String cleTransaction(LocalDate date, String libelle,
                                  BigDecimal montant, String type) {
        return date + "|" + normaliser(libelle) + "|" + montantNormalise(montant) + "|" + type;
    }

    private String normaliser(String valeur) {
        return valeur == null ? "" : valeur.trim().toLowerCase();
    }

    /** 30 et 30.00 doivent donner la meme cle. */
    private String montantNormalise(BigDecimal montant) {
        return montant == null ? "" : montant.stripTrailingZeros().toPlainString();
    }
}
