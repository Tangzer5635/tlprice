package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * « Combien puis-je virer sur mon livret sans passer sous mon seuil ? »
 *
 * On expose le chiffre ET son raisonnement : le creux de la periode et la
 * date ou il tombe. Un montant seul serait un oracle ; avec le creux, on
 * comprend d ou il sort et on peut le contester.
 */
@Getter
@AllArgsConstructor
public class CapaciteEpargneDto {

    /** Montant virable sans passer sous le seuil. Jamais negatif. */
    private BigDecimal capacite;

    /** Solde le plus bas atteint sur la periode, avant virement. */
    private BigDecimal creux;

    /** Jour ou ce creux est atteint. */
    private LocalDate dateCreux;

    /** Seuil de securite utilise (0 si aucun n est configure). */
    private BigDecimal seuil;

    /** Fin de la periode examinee. */
    private LocalDate horizon;

    /** Vrai si le seuil vient des reglages, faux si on a pris zero par defaut. */
    private boolean seuilConfigure;
}
