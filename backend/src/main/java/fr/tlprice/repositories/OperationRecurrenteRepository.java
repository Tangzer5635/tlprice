package fr.tlprice.repositories;

import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperationRecurrenteRepository extends JpaRepository<OperationRecurrente, Long> {
    List<OperationRecurrente> findByUtilisateurOrderByLibelleAsc(Utilisateur utilisateur);
    List<OperationRecurrente> findByUtilisateurAndActiveTrue(Utilisateur utilisateur);
    Optional<OperationRecurrente> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
