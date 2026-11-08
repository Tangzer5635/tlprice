package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * La session Enable Banking active d un utilisateur : identifiant de
 * session, uid du compte bancaire et fin de validite du consentement.
 */
@Entity
@Table(name = "connexion_bancaire")
@Getter
@Setter
@NoArgsConstructor
public class ConnexionBancaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String accountUid;

    @Column(nullable = false)
    private OffsetDateTime validUntil;

    @Column(nullable = false)
    private OffsetDateTime dateConnexion;
}
