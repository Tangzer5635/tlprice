package fr.tlprice.entities;

/** Resultat de la confrontation entre une echeance prevue et la banque. */
public enum StatutRapprochement {
    /** Trouve en banque, montant identique. Rien a faire. */
    CONFIRME,
    /** Trouve en banque, mais le montant debite differe du montant prevu. */
    ECART,
    /** La date est passee et rien de correspondant n a ete debite. */
    MANQUANT
}
