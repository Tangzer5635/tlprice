package fr.tlprice.repositories;

import fr.tlprice.entities.HistoriqueSolde;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HistoriqueSoldeRepository extends JpaRepository<HistoriqueSolde, Long> {

    Optional<HistoriqueSolde> findByUtilisateurAndDate(Utilisateur utilisateur, LocalDate date);

    List<HistoriqueSolde> findByUtilisateurAndDateGreaterThanEqualOrderByDateAsc(
        Utilisateur utilisateur, LocalDate depuis);

    List<HistoriqueSolde> findByUtilisateurOrderByDateAsc(Utilisateur utilisateur);

    void deleteByUtilisateurAndDateBefore(Utilisateur utilisateur, LocalDate avant);
}
