package fr.tlprice.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ParametresDto {
    @Email
    private String email;
    private boolean alertesEmail;
    private BigDecimal seuilAlerte;

    /** Heure d envoi du recap EMAIL, 0-23 (heure de Paris). Defaut 8. */
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(23)
    private int heureRecap = 8;

    /** Push quotidien sur les appareils abonnes. */
    private boolean pushActif;

    /** Heure d envoi du PUSH, 0-23 (heure de Paris). Defaut 7. */
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(23)
    private int heureRecapPush = 7;

    /** Envoyer le push meme les jours sans mouvement. */
    private boolean pushSiVide;
}
