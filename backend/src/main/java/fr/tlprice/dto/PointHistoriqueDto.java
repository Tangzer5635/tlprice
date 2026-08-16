package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un point de la courbe du passe. */
@Getter
@AllArgsConstructor
public class PointHistoriqueDto {
    private LocalDate date;
    private BigDecimal soldeReel;
    private int fraicheurJours;
}
