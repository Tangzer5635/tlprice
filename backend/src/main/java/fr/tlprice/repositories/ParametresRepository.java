package fr.tlprice.repositories;

import fr.tlprice.entities.Parametres;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParametresRepository extends JpaRepository<Parametres, Long> {
    Optional<Parametres> findByUtilisateur(Utilisateur utilisateur);
    List<Parametres> findByAlertesEmailTrue();
    List<Parametres> findByPushActifTrue();
}
