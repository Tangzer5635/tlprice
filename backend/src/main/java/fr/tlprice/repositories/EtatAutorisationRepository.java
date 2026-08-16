package fr.tlprice.repositories;

import fr.tlprice.entities.EtatAutorisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface EtatAutorisationRepository extends JpaRepository<EtatAutorisation, String> {
    void deleteByDateCreationBefore(OffsetDateTime limite);
}
