package fr.tlprice.repositories;

import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.RapprochementEcheance;
import fr.tlprice.entities.StatutRapprochement;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RapprochementRepository extends JpaRepository<RapprochementEcheance, Long> {

    Optional<RapprochementEcheance> findByOperationAndDateEcheance(
        OperationRecurrente operation, LocalDate dateEcheance);

    Optional<RapprochementEcheance> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);

    List<RapprochementEcheance> findByUtilisateurOrderByDateEcheanceDesc(Utilisateur utilisateur);

    List<RapprochementEcheance> findByUtilisateurAndTraiteFalseAndStatutInOrderByDateEcheanceDesc(
        Utilisateur utilisateur, List<StatutRapprochement> statuts);

    long countByUtilisateurAndTraiteFalseAndStatutIn(
        Utilisateur utilisateur, List<StatutRapprochement> statuts);

    /** Lignes de controle rattachees a une operation, a purger avant de la supprimer. */
    long countByOperation(OperationRecurrente operation);
    void deleteByOperation(OperationRecurrente operation);
}
