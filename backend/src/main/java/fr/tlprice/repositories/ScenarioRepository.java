package fr.tlprice.repositories;

import fr.tlprice.entities.Scenario;
import fr.tlprice.entities.Utilisateur;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    /*
     * @EntityGraph charge les lignes dans la meme requete.
     *
     * Sans ca, la collection reste un proxy paresseux : la transaction du
     * service se referme avant que le controleur ne construise le DTO, et la
     * premiere lecture de getLignes() leve une LazyInitializationException —
     * une erreur 500 qui n apparait qu une fois qu il existe au moins un
     * scenario, donc jamais pendant le developpement d une liste vide.
     */
    @EntityGraph(attributePaths = "lignes")
    List<Scenario> findByUtilisateurOrderByDateCreationDesc(Utilisateur utilisateur);

    @EntityGraph(attributePaths = "lignes")
    Optional<Scenario> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
