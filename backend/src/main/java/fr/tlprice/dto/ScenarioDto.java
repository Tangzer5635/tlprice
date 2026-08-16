package fr.tlprice.dto;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.TypeOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ScenarioDto {

    private Long id;

    @NotBlank(message = "Donne un nom a ce scenario.")
    private String nom;

    private List<LigneDto> lignes = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LigneDto {
        private Long id;

        @NotBlank(message = "Chaque ligne a besoin d un libelle.")
        private String libelle;

        @NotNull(message = "Chaque ligne a besoin d un montant.")
        private BigDecimal montant;

        private TypeOperation type = TypeOperation.DEBIT;
        private Categorie categorie = Categorie.AUTRE;

        @NotNull(message = "Chaque ligne a besoin d une date.")
        private LocalDate date;
    }
}
