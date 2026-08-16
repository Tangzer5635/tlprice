package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Override ponctuel d une echeance recurrente pour une date donnee :
 * salaire different ce mois-ci, prime, prelevement suspendu, etc.
 * Ne modifie pas le montant par defaut de l operation.
 */
@Entity
@Table(
    name = "exception_echeance",
    uniqueConstraints = @UniqueConstraint(columnNames = {"operation_id", "date_echeance"})
)
@Getter
@Setter
@NoArgsConstructor
public class ExceptionEcheance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private OperationRecurrente operation;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate date;

    /** Nouveau montant ; null = pas de changement de montant, seule annulation possible. */
    @Column(precision = 12, scale = 2)
    private BigDecimal montantOverride;

    /** true = l echeance de ce mois-ci n est PAS comptee. */
    @Column(nullable = false)
    private boolean annulee = false;
}
