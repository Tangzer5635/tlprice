package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Une case du calendrier : sa date, ses mouvements et le solde en fin de journee. */
@Getter
@AllArgsConstructor
public class JourCalendrierDto {
    private LocalDate date;
    private List<MouvementDto> mouvements;
    private BigDecimal soldeProjete;
}
