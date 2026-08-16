package fr.tlprice.dto;

import fr.tlprice.entities.ConfigSmtp;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Vue safe : sans le mot de passe. */
@Getter
@AllArgsConstructor
public class ConfigSmtpDto {
    private String host;
    private int port;
    private String username;
    private String expediteur;
    private boolean starttls;
    private boolean configuree;

    public static ConfigSmtpDto de(ConfigSmtp c) {
        if (c == null) return new ConfigSmtpDto(null, 587, null, null, true, false);
        return new ConfigSmtpDto(
            c.getHost(), c.getPort(), c.getUsername(),
            c.getExpediteur(), c.isStarttls(), true);
    }
}
