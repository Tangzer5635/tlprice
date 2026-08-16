package fr.tlprice.repositories;

import fr.tlprice.entities.ConnexionBancaire;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnexionBancaireRepository extends JpaRepository<ConnexionBancaire, Long> {
    Optional<ConnexionBancaire> findByUtilisateur(Utilisateur utilisateur);
    void deleteByUtilisateur(Utilisateur utilisateur);
}
