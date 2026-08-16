package fr.tlprice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une operation ponctuelle : un achat carte, un virement recu...
 * Le champ debiteeEnBanque gere le delai d affichage de la banque :
 * une transaction faite mais pas encore visible sur le solde banque
 * est prise en compte dans le solde projete.
 */
@Entity
@Table(name = "transaction_ponctuelle")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @NotBlank
    @Column(nullable = false)
    private String libelle;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeOperation type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateOperation;

    /** true = deja repercutee sur le solde affiche par la banque. */
    @Column(nullable = false)
    private boolean debiteeEnBanque = false;
}
