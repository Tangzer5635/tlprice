package fr.tlprice.entities;

/** A quel point la correspondance echeance / ligne bancaire est sure. */
public enum ConfianceRapprochement {
    /** Les libelles se recoupent : correspondance fiable. */
    CERTAIN,
    /** Deduite du montant et de la date seuls : a confirmer par l utilisateur. */
    PROBABLE,
    /** Aucune correspondance trouvee. */
    AUCUNE
}
