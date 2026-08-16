package fr.tlprice.repositories;

import fr.tlprice.entities.Transaction;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUtilisateurOrderByDateOperationDesc(Utilisateur utilisateur);
    List<Transaction> findByUtilisateurAndDebiteeEnBanqueFalse(Utilisateur utilisateur);

    /** Transactions d une periode, bornes incluses (export CSV / Excel). */
    List<Transaction> findByUtilisateurAndDateOperationBetweenOrderByDateOperationAsc(
        Utilisateur utilisateur, LocalDate debut, LocalDate fin);
    Optional<Transaction> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
