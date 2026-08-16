package fr.tlprice.services;

import fr.tlprice.dto.ConfigSmtpDto;
import fr.tlprice.dto.ConfigSmtpEntreeDto;
import fr.tlprice.entities.ConfigSmtp;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ConfigSmtpRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Properties;

/**
 * Gestion de la config SMTP par utilisateur : lecture/ecriture/suppression,
 * et construction d un JavaMailSender pret a l emploi.
 */
@Service
public class SmtpUtilisateurService {

    private final ConfigSmtpRepository repository;
    private final ChiffrementService chiffrement;

    public SmtpUtilisateurService(ConfigSmtpRepository repository,
                                  ChiffrementService chiffrement) {
        this.repository = repository;
        this.chiffrement = chiffrement;
    }

    @Transactional(readOnly = true)
    public ConfigSmtpDto lire(Utilisateur utilisateur) {
        return ConfigSmtpDto.de(repository.findByUtilisateur(utilisateur).orElse(null));
    }

    @Transactional(readOnly = true)
    public Optional<ConfigSmtp> chercher(Utilisateur utilisateur) {
        return repository.findByUtilisateur(utilisateur);
    }

    @Transactional
    public ConfigSmtpDto enregistrer(Utilisateur utilisateur, ConfigSmtpEntreeDto dto) {
        ConfigSmtp config = repository.findByUtilisateur(utilisateur).orElseGet(() -> {
            ConfigSmtp c = new ConfigSmtp();
            c.setUtilisateur(utilisateur);
            return c;
        });
        config.setHost(dto.getHost().trim());
        config.setPort(dto.getPort());
        config.setUsername(dto.getUsername().trim());
        // Mot de passe : ne remplace que si un nouveau est fourni
        if (dto.getMotDePasse() != null && !dto.getMotDePasse().isBlank()) {
            config.setMotDePasseChiffre(chiffrement.chiffrer(dto.getMotDePasse()));
        } else if (config.getMotDePasseChiffre() == null) {
            throw new IllegalStateException(
                "Mot de passe requis pour la premiere configuration.");
        }
        config.setExpediteur(dto.getExpediteur() == null || dto.getExpediteur().isBlank()
            ? config.getUsername() : dto.getExpediteur().trim());
        config.setStarttls(dto.isStarttls());
        return ConfigSmtpDto.de(repository.save(config));
    }

    @Transactional
    public void supprimer(Utilisateur utilisateur) {
        repository.deleteByUtilisateur(utilisateur);
    }

    /** Construit un sender pret a l emploi a partir de la config user. */
    public JavaMailSender construireSender(ConfigSmtp config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(chiffrement.dechiffrer(config.getMotDePasseChiffre()));
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(config.isStarttls()));
        // Timeouts raisonnables (10 s) pour ne pas bloquer l app
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
