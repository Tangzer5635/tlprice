package fr.tlprice.dto;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.TypeOperation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un mouvement futur pris en compte dans le solde projete :
 * soit une echeance d operation recurrente, soit une transaction
 * ponctuelle pas encore debitee en banque.
 */
@Getter
@AllArgsConstructor
public class MouvementDto {
    private LocalDate date;
    private String libelle;
    private BigDecimal montant;
    private TypeOperation type;
    private Categorie categorie;
    /** \"ECHEANCE\" ou \"TRANSACTION\" */
    private String origine;
    /** id de l operation recurrente source (null pour les transactions). */
    private Long operationId;
    /** Montant par defaut si une exception a modifie ce mois-ci. Null sinon. */
    private BigDecimal montantOriginal;
}
