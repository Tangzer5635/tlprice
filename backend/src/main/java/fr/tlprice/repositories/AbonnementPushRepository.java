package fr.tlprice.repositories;

import fr.tlprice.entities.AbonnementPush;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AbonnementPushRepository extends JpaRepository<AbonnementPush, Long> {
    List<AbonnementPush> findByUtilisateur(Utilisateur utilisateur);
    Optional<AbonnementPush> findByEndpoint(String endpoint);
    long countByUtilisateur(Utilisateur utilisateur);
}
