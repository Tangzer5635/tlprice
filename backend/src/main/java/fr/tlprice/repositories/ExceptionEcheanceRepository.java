package fr.tlprice.repositories;

import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExceptionEcheanceRepository extends JpaRepository<ExceptionEcheance, Long> {
    List<ExceptionEcheance> findByOperationIn(List<OperationRecurrente> operations);
    Optional<ExceptionEcheance> findByOperationAndDate(OperationRecurrente op, LocalDate date);
    List<ExceptionEcheance> findByOperation_Utilisateur(Utilisateur utilisateur);

    /** Ajustements ponctuels rattaches a une operation, a purger avant de la supprimer. */
    long countByOperation(OperationRecurrente operation);
    void deleteByOperation(OperationRecurrente operation);
}
