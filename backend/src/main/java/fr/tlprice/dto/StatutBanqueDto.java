package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class StatutBanqueDto {
    /** Application ID + cle privee fournis ? */
    private boolean configuree;
    private String applicationId;
    /** Banque choisie ? */
    private String nomBanque;
    private String paysBanque;
    /** Session active ? */
    private boolean connectee;
    private OffsetDateTime validUntil;
}
