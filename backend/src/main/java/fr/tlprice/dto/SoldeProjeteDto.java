package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class SoldeProjeteDto {
    private BigDecimal soldeReel;
    private LocalDate dateMaj;
    private LocalDate dateProjection;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal soldeProjete;
    private List<MouvementDto> mouvements;
}
