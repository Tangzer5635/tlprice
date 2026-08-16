package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Verdict du seuil d alerte pour l utilisateur. */
@Getter
@AllArgsConstructor
public class AlerteSoldeDto {
    private BigDecimal seuil;
    private BigDecimal soldeMinimum;
    private LocalDate dateMinimum;
    private boolean alerte;
}
