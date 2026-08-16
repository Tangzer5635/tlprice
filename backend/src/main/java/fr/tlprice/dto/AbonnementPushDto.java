package fr.tlprice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ce que le navigateur renvoie apres pushManager.subscribe(). */
@Getter
@Setter
@NoArgsConstructor
public class AbonnementPushDto {

    @NotBlank
    private String endpoint;

    @NotBlank
    private String cleP256dh;

    @NotBlank
    private String cleAuth;

    /** Libelle libre choisi cote client ("iPhone", "PC bureau"...). */
    private String appareil;
}
