package fr.tlprice.dto;

import fr.tlprice.entities.ConfianceRapprochement;
import fr.tlprice.entities.StatutRapprochement;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Une ligne de l ecran de controle prevu / reel. */
@Getter
@AllArgsConstructor
public class RapprochementDto {
    private Long id;
    private Long operationId;
    private String libelle;
    private LocalDate dateEcheance;
    private LocalDate dateBancaire;
    private BigDecimal montantPrevu;
    private BigDecimal montantReel;
    /** montantReel - montantPrevu. Null si MANQUANT. */
    private BigDecimal ecart;
    private String libelleBancaire;
    private StatutRapprochement statut;
    private ConfianceRapprochement confiance;
    private boolean traite;
}
