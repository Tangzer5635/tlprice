package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Preferences de notification par utilisateur. */
@Entity
@Table(name = "parametres")
@Getter
@Setter
@NoArgsConstructor
public class Parametres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    /** Adresse ou envoyer le recap (par defaut : email du compte). */
    private String email;

    /** Recap quotidien par email. */
    @Column(nullable = false)
    private boolean alertesEmail = false;

    /** Heure d envoi du recap EMAIL (0-23, heure de Paris). */
    @Column(nullable = false)
    private int heureRecap = 8;

    /** Notification push quotidienne sur les appareils abonnes. */
    @Column(nullable = false)
    private boolean pushActif = false;

    /**
     * Heure d envoi du push (0-23, heure de Paris). Independante de
     * heureRecap : le mail du matin et le rappel des prelevements du jour
     * n ont pas la meme utilite au meme moment.
     */
    @Column(nullable = false)
    private int heureRecapPush = 7;

    /**
     * Envoyer le push meme les jours sans mouvement. Par defaut non : une
     * notification qui ne dit rien apprend a etre ignoree.
     */
    @Column(nullable = false)
    private boolean pushSiVide = false;

    /**
     * Seuil d alerte : si le solde projete des 30 prochains jours passe
     * dessous, un avertissement est affiche sur le dashboard et inclus dans
     * le recap. null = seuil desactive.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal seuilAlerte;

    /** Date du dernier envoi de recap email, pour eviter les doublons. */
    private LocalDate dernierRecap;

    /** Date du dernier push quotidien, pour eviter les doublons. */
    private LocalDate dernierPush;
}
