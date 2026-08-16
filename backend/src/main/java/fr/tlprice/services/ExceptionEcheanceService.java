package fr.tlprice.services;

import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ExceptionEcheanceService {

    private final ExceptionEcheanceRepository exceptionRepository;
    private final OperationRecurrenteRepository operationRepository;

    public ExceptionEcheanceService(ExceptionEcheanceRepository exceptionRepository,
                                     OperationRecurrenteRepository operationRepository) {
        this.exceptionRepository = exceptionRepository;
        this.operationRepository = operationRepository;
    }

    /** Upsert : cree ou met a jour l exception pour (operation, date). */
    @Transactional
    public ExceptionEcheance ajusterOuAnnuler(Utilisateur utilisateur, Long operationId,
                                              LocalDate date, BigDecimal nouveauMontant,
                                              boolean annulee) {
        OperationRecurrente op = operationRepository.findByIdAndUtilisateur(operationId, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Operation introuvable"));

        ExceptionEcheance ex = exceptionRepository.findByOperationAndDate(op, date)
            .orElseGet(() -> {
                ExceptionEcheance e = new ExceptionEcheance();
                e.setOperation(op);
                e.setDate(date);
                return e;
            });
        ex.setMontantOverride(annulee ? null : nouveauMontant);
        ex.setAnnulee(annulee);
        return exceptionRepository.save(ex);
    }

    @Transactional
    public void reinitialiser(Utilisateur utilisateur, Long operationId, LocalDate date) {
        OperationRecurrente op = operationRepository.findByIdAndUtilisateur(operationId, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Operation introuvable"));
        exceptionRepository.findByOperationAndDate(op, date)
            .ifPresent(exceptionRepository::delete);
    }
}
