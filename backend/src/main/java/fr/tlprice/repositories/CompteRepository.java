package fr.tlprice.repositories;

import fr.tlprice.entities.Compte;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompteRepository extends JpaRepository<Compte, Long> {
    Optional<Compte> findByUtilisateur(Utilisateur utilisateur);
}
