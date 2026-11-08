package fr.tlprice.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Loyer, salaire, abonnement... Sert a generer les echeances futures
 * pour le calcul du solde projete.
 */
@Entity
@Table(name = "operation_recurrente")
@Getter
@Setter
@NoArgsConstructor
public class OperationRecurrente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
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
    private Frequence frequence;

    /**
     * Jour de prelevement pour la frequence MENSUELLE (1-31, plafonne
     * au dernier jour du mois). Ignore pour les autres frequences.
     */
    @Min(1)
    @Max(31)
    private Integer jourDuMois;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateDebut;

    private LocalDate dateFin;

    @Column(nullable = false)
    private boolean active = true;
}
