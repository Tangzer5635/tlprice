package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Le verdict du rapprochement pour UNE echeance a UNE date.
 *
 * Sans cette table, l analyse repartirait de zero a chaque affichage et
 * l utilisateur reverrait indefiniment les ecarts qu il a deja traites.
 * La contrainte d unicite (operation, date) garantit qu une echeance n a
 * qu une seule ligne, mise a jour d une analyse a l autre.
 */
@Entity
@Table(
    name = "rapprochement",
    uniqueConstraints = @UniqueConstraint(columnNames = {"operation_id", "date_echeance"})
)
@Getter
@Setter
@NoArgsConstructor
public class RapprochementEcheance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private OperationRecurrente operation;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    /** Ce que tlprice annoncait pour cette date. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montantPrevu;

    /** Ce que la banque a reellement debite. Null si MANQUANT. */
    @Column(precision = 12, scale = 2)
    private BigDecimal montantReel;

    /** Libelle brut de la ligne bancaire retenue. */
    @Column(length = 300)
    private String libelleBancaire;

    /** Reference de la ligne bancaire, pour ne pas la reutiliser ailleurs. */
    @Column(length = 200)
    private String referenceBancaire;

    /** Date reelle du debit en banque (peut differer de l echeance prevue). */
    private LocalDate dateBancaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutRapprochement statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfianceRapprochement confiance = ConfianceRapprochement.AUCUNE;

    /** L utilisateur a tranche : on ne le represente plus. */
    @Column(nullable = false)
    private boolean traite = false;

    @Column(nullable = false)
    private OffsetDateTime dateDetection = OffsetDateTime.now();
}
