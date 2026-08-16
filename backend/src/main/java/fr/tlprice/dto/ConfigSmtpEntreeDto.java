package fr.tlprice.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConfigSmtpEntreeDto {
    @NotBlank private String host;
    @Min(1) @Max(65535) private int port = 587;
    @NotBlank private String username;
    /** Optionnel a la modification : si vide, on garde l ancien mot de passe. */
    private String motDePasse;
    @Email private String expediteur;
    private boolean starttls = true;
}
