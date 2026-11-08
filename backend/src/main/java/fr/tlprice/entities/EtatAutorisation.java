package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * State OAuth genere avant la redirection vers la banque.
 * Verifie puis consomme au retour (protection CSRF + routage utilisateur).
 */
@Entity
@Table(name = "etat_autorisation")
@Getter
@Setter
@NoArgsConstructor
public class EtatAutorisation {

    @Id
    private String state;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private OffsetDateTime dateCreation = OffsetDateTime.now();
}
