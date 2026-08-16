package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Le solde reel connu (saisi a la main ou synchronise depuis la banque)
 * et sa date de mise a jour. Un compte par utilisateur.
 */
@Entity
@Table(name = "compte")
@Getter
@Setter
@NoArgsConstructor
public class Compte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate dateMaj = LocalDate.now();
}
