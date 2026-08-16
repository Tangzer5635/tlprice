package fr.tlprice.dto;

import fr.tlprice.entities.Categorie;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/** Transaction bancaire enrichie pour l ecran d import. */
@Getter
@AllArgsConstructor
public class TransactionBancaireDto {
    private String reference;
    private String date;
    /** Libelle brut renvoye par la banque. */
    private String libelle;
    /** Libelle transforme par une regle apprise, null sinon. */
    private String libelleAuto;
    /** Categorie deduite par la regle, null sinon. */
    private Categorie categorieAuto;
    /** true si une transaction ou echeance identique est deja en base. */
    private boolean dejaImporte;
    private BigDecimal montant;
    private String type;
}
