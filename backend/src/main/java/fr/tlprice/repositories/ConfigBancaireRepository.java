package fr.tlprice.repositories;

import fr.tlprice.entities.ConfigBancaire;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigBancaireRepository extends JpaRepository<ConfigBancaire, Long> {
    Optional<ConfigBancaire> findByUtilisateur(Utilisateur utilisateur);
}
