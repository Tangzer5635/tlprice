package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Confrontation d une projection passee au solde reellement atteint.
 * Null partout tant qu il n y a pas assez d historique.
 */
@Getter
@AllArgsConstructor
public class FiabiliteDto {
    /** Date a laquelle la projection avait ete faite. */
    private LocalDate dateProjection;
    /** Ce qui etait annonce pour aujourd hui. */
    private BigDecimal annonce;
    /** Ce qui a ete constate. */
    private BigDecimal constate;
    /** constate - annonce. Positif = on a eu plus que prevu. */
    private BigDecimal ecart;
    /** Nombre de jours d historique disponibles. */
    private int joursHistorique;
}
