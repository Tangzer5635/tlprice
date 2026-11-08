package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un point par jour et par utilisateur : le solde reellement constate et
 * la projection faite ce jour-la.
 *
 * On stocke les deux, et pas seulement le reel. Le reel seul donne une
 * jolie courbe du passe mais ne dit pas si le modele de projection tenait.
 * En gardant, pour chaque jour, ce qu on annoncait a 30 jours, on peut
 * comparer plus tard cette annonce au solde effectivement atteint : c est
 * la seule facon de savoir si les previsions valent quelque chose.
 */
@Entity
@Table(
    name = "historique_solde",
    uniqueConstraints = @UniqueConstraint(columnNames = {"utilisateur_id", "date_point"})
)
@Getter
@Setter
@NoArgsConstructor
public class HistoriqueSolde {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Utilisateur utilisateur;

    @Column(name = "date_point", nullable = false)
    private LocalDate date;

    /** Solde bancaire connu ce jour-la. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal soldeReel;

    /** Ce que tlprice annoncait ce jour-la pour dans 30 jours. */
    @Column(precision = 12, scale = 2)
    private BigDecimal soldeProjete;

    /**
     * Date a laquelle se rapporte soldeProjete (date du point + 30 jours).
     * Stockee explicitement : si l horizon change un jour, les anciens
     * points restent interpretables.
     */
    private LocalDate dateProjection;

    /**
     * Age du solde bancaire au moment de la capture. Zero = synchronise le
     * jour meme. Permet de ne pas confondre "solde stable" et "solde plus
     * rafraichi depuis une semaine".
     */
    @Column(nullable = false)
    private int fraicheurJours = 0;
}
