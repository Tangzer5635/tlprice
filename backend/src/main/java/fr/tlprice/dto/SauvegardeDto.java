package fr.tlprice.dto;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.Frequence;
import fr.tlprice.entities.TypeOperation;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Photo complete des donnees d un utilisateur, pour transferer un compte
 * d une instance a une autre (poste local vers Raspberry Pi, par exemple).
 *
 * Volontairement absent : les connexions bancaires, la config SMTP et la
 * cle privee Enable Banking. Ces donnees sont chiffrees avec la cle AES de
 * l instance, qui differe d une machine a l autre : les reimporter ne
 * donnerait que du contenu indechiffrable.
 */
@Getter
@Setter
@NoArgsConstructor
public class SauvegardeDto {

    /**
     * Format du fichier, pour refuser proprement un fichier etranger.
     * Volontairement sans valeur par defaut : un JSON quelconque doit
     * arriver avec un format null et se faire rejeter a l import.
     */
    private String format;

    private int version = 1;

    private OffsetDateTime dateExport;

    /** Email du compte source, purement informatif. */
    private String compteSource;

    /** Solde reel au moment de l export (applique seulement si demande). */
    private BigDecimal solde;

    private LocalDate soldeDateMaj;

    private ParametresSauvegarde parametres;

    private List<OperationSauvegarde> operations = new ArrayList<>();

    private List<TransactionSauvegarde> transactions = new ArrayList<>();

    private List<RegleSauvegarde> regles = new ArrayList<>();

    // ------------------------------------------------------------------

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ParametresSauvegarde {
        private String email;
        private boolean alertesEmail;
        private int heureRecap = 8;
        private BigDecimal seuilAlerte;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OperationSauvegarde {
        private String libelle;
        private BigDecimal montant;
        private TypeOperation type;
        private Frequence frequence;
        private Integer jourDuMois;
        private Categorie categorie;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private boolean active = true;
        /** Ajustements ponctuels rattaches a cette operation. */
        private List<ExceptionSauvegarde> exceptions = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ExceptionSauvegarde {
        private LocalDate date;
        private BigDecimal montantOverride;
        private boolean annulee;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TransactionSauvegarde {
        private String libelle;
        private BigDecimal montant;
        private TypeOperation type;
        private Categorie categorie;
        private LocalDate dateOperation;
        private boolean debiteeEnBanque;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RegleSauvegarde {
        private String motif;
        private String libelleFinal;
        private Categorie categorie;
    }
}
