package fr.tlprice.dto;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.TypeOperation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une ligne du fichier exporte (CSV ou Excel). Regroupe sous la meme
 * forme les transactions ponctuelles et les echeances des operations
 * recurrentes generees sur la periode.
 */
@Getter
@AllArgsConstructor
public class LigneExportDto {

    private LocalDate date;
    private String libelle;
    private Categorie categorie;
    private TypeOperation type;
    /** Montant toujours positif, comme en base. */
    private BigDecimal montant;
    /** "Ponctuel" ou "Recurrent". */
    private String origine;
    /** Etat lisible : "Debitee en banque", "En attente", "Echeance prevue"... */
    private String statut;

    /** Montant signe : negatif pour un debit. Pratique pour les sommes Excel. */
    public BigDecimal getMontantSigne() {
        return type == TypeOperation.DEBIT ? montant.negate() : montant;
    }
}
