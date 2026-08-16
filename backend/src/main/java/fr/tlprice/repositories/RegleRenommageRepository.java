package fr.tlprice.repositories;

import fr.tlprice.entities.RegleRenommage;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegleRenommageRepository extends JpaRepository<RegleRenommage, Long> {
    List<RegleRenommage> findByUtilisateurOrderByLibelleFinalAsc(Utilisateur utilisateur);
    Optional<RegleRenommage> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
