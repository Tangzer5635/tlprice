package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Un abonnement Web Push : un appareil (un navigateur, ou l app installee
 * sur l ecran d accueil d un iPhone) autorise a recevoir des notifications.
 *
 * Un utilisateur peut en avoir plusieurs (iPhone + PC de bureau). L endpoint
 * est fourni par le navigateur et identifie l appareil de facon unique.
 */
@Entity
@Table(name = "abonnement_push")
@Getter
@Setter
@NoArgsConstructor
public class AbonnementPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** URL du service de push (web.push.apple.com pour iOS). Peut etre longue. */
    @Column(nullable = false, length = 1000, unique = true)
    private String endpoint;

    /** Cle publique de l appareil (base64url), pour chiffrer le message. */
    @Column(nullable = false, length = 255)
    private String cleP256dh;

    /** Secret d authentification de l appareil (base64url). */
    @Column(nullable = false, length = 255)
    private String cleAuth;

    /** Pour que l utilisateur reconnaisse ses appareils dans la liste. */
    @Column(length = 120)
    private String appareil;

    /**
     * Heure d envoi propre a cet appareil (0-23), en heure de Paris.
     *
     * NULL = suit l heure globale du compte. C est volontaire : la colonne est
     * ajoutee sur une table deja peuplee, elle doit donc etre nullable pour
     * que ddl-auto=update passe sans casse — et NULL a ici un sens utile
     * plutot que d etre un trou a combler.
     *
     * Permet un iPhone a 12h et un PC a 22h sur le meme compte.
     */
    @Column(name = "heure_recap")
    private Integer heureRecap;

    /** Dernier jour ou cet appareil a recu le recap, pour ne pas doubler. */
    @Column(name = "dernier_push")
    private java.time.LocalDate dernierPush;

    @Column(nullable = false)
    private OffsetDateTime dateCreation = OffsetDateTime.now();

    /** Heure effective : la sienne si definie, sinon celle du compte. */
    public int heureEffective(int heureDuCompte) {
        return heureRecap == null ? heureDuCompte : heureRecap;
    }
}
