package fr.tlprice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compte rendu affiche a l utilisateur apres un import de sauvegarde. */
@Getter
@Setter
@NoArgsConstructor
public class RapportImportDto {

    private String mode;

    private int operationsAjoutees;
    private int operationsIgnorees;
    private int exceptionsAjoutees;

    private int transactionsAjoutees;
    private int transactionsIgnorees;

    private int reglesAjoutees;
    private int reglesIgnorees;

    private boolean soldeApplique;
    private boolean parametresAppliques;

    /** Phrase prete a afficher dans l interface. */
    public String getResume() {
        StringBuilder sb = new StringBuilder();
        sb.append(operationsAjoutees).append(" operation(s) recurrente(s)");
        if (exceptionsAjoutees > 0) {
            sb.append(" (dont ").append(exceptionsAjoutees).append(" ajustement(s))");
        }
        sb.append(", ").append(transactionsAjoutees).append(" transaction(s)");
        sb.append(" et ").append(reglesAjoutees).append(" regle(s) importee(s).");

        int ignores = operationsIgnorees + transactionsIgnorees + reglesIgnorees;
        if (ignores > 0) {
            sb.append(" ").append(ignores).append(" doublon(s) ignore(s).");
        }
        if (soldeApplique) sb.append(" Solde mis a jour.");
        if (parametresAppliques) sb.append(" Parametres mis a jour.");
        return sb.toString();
    }
}
