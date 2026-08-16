package fr.tlprice.repositories;

import fr.tlprice.entities.ConfigSmtp;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfigSmtpRepository extends JpaRepository<ConfigSmtp, Long> {
    Optional<ConfigSmtp> findByUtilisateur(Utilisateur utilisateur);
    void deleteByUtilisateur(Utilisateur utilisateur);
}
