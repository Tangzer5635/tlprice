package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un mouvement hypothetique appartenant a un scenario.
 *
 * Volontairement proche d une Transaction : meme forme, meme categories,
 * pour que l injection dans la projection ne demande aucune conversion
 * particuliere. La difference tient a l origine, qui vaut SCENARIO et permet
 * de distinguer ces lignes a l ecran.
 */
@Entity
@Table(name = "ligne_scenario")
@Getter
@Setter
@NoArgsConstructor
public class LigneScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(nullable = false, length = 120)
    private String libelle;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeOperation type = TypeOperation.DEBIT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Categorie categorie = Categorie.AUTRE;

    @Column(nullable = false)
    private LocalDate date;
}
