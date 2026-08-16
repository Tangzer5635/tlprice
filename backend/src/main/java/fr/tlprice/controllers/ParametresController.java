package fr.tlprice.controllers;

import fr.tlprice.dto.AlerteSoldeDto;
import fr.tlprice.dto.ConfigSmtpDto;
import fr.tlprice.dto.ConfigSmtpEntreeDto;
import fr.tlprice.dto.ParametresDto;
import fr.tlprice.entities.Parametres;
import fr.tlprice.services.NotificationService;
import fr.tlprice.services.SmtpUtilisateurService;
import fr.tlprice.services.SoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parametres")
public class ParametresController {

    private final SoldeService soldeService;
    private final NotificationService notifications;
    private final SmtpUtilisateurService smtp;
    private final UtilisateurCourantService utilisateurCourant;

    public ParametresController(SoldeService soldeService,
                                NotificationService notifications,
                                SmtpUtilisateurService smtp,
                                UtilisateurCourantService utilisateurCourant) {
        this.soldeService = soldeService;
        this.notifications = notifications;
        this.smtp = smtp;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public Parametres recuperer() {
        return soldeService.parametres(utilisateurCourant.recuperer());
    }

    @PutMapping
    public Parametres modifier(@RequestBody @Valid ParametresDto dto) {
        return soldeService.majParametres(utilisateurCourant.recuperer(), dto);
    }

    @GetMapping("/alerte")
    public AlerteSoldeDto alerte() {
        return soldeService.verifierSeuil(utilisateurCourant.recuperer());
    }

    @PostMapping("/test-email")
    public Map<String, String> testerEmail() {
        return Map.of("statut", notifications.envoyerRecap(utilisateurCourant.recuperer()));
    }

    @GetMapping("/mail-disponible")
    public Map<String, Boolean> mailDisponible() {
        return Map.of("disponible",
            notifications.mailDisponible(utilisateurCourant.recuperer()));
    }

    // ------------ Configuration SMTP par utilisateur ------------

    @GetMapping("/smtp")
    public ConfigSmtpDto lireSmtp() {
        return smtp.lire(utilisateurCourant.recuperer());
    }

    @PutMapping("/smtp")
    public ConfigSmtpDto enregistrerSmtp(@RequestBody @jakarta.validation.Valid ConfigSmtpEntreeDto dto) {
        return smtp.enregistrer(utilisateurCourant.recuperer(), dto);
    }

    @DeleteMapping("/smtp")
    public Map<String, Boolean> supprimerSmtp() {
        smtp.supprimer(utilisateurCourant.recuperer());
        return Map.of("supprime", true);
    }
}
