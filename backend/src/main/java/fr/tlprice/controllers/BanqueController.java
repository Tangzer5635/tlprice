package fr.tlprice.controllers;

import fr.tlprice.dto.BanqueDto;
import fr.tlprice.dto.TransactionBancaireDto;
import fr.tlprice.dto.StatutBanqueDto;
import fr.tlprice.entities.Compte;
import fr.tlprice.services.EnableBankingService;
import fr.tlprice.services.HistoriqueSoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/banque")
public class BanqueController {

    private final EnableBankingService enableBankingService;
    private final HistoriqueSoldeService historique;
    private final UtilisateurCourantService utilisateurCourant;
    private final String redirectUrl;

    public BanqueController(EnableBankingService enableBankingService,
                            HistoriqueSoldeService historique,
                            UtilisateurCourantService utilisateurCourant,
                            @Value("${tlprice.redirect-url}") String redirectUrl) {
        this.enableBankingService = enableBankingService;
        this.historique = historique;
        this.utilisateurCourant = utilisateurCourant;
        this.redirectUrl = redirectUrl;
    }

    @GetMapping("/statut")
    public StatutBanqueDto statut() {
        return enableBankingService.recupererStatut(utilisateurCourant.recuperer());
    }

    /**
     * L URL de callback a declarer dans le champ "Allowed redirect URLs"
     * de l application Enable Banking de chaque utilisateur.
     */
    @GetMapping("/redirect-url")
    public Map<String, Object> redirectUrl() {
        Map<String, Object> corps = new java.util.HashMap<>();
        corps.put("url", redirectUrl == null ? "" : redirectUrl);
        // L ecran Banque affiche un avertissement au lieu de laisser
        // l utilisateur declarer une URL locale chez Enable Banking et
        // decouvrir le probleme apres s etre authentifie a sa banque.
        corps.put("locale", fr.tlprice.services.EnableBankingService.urlLocale(redirectUrl));
        corps.put("absente", redirectUrl == null || redirectUrl.isBlank());
        return corps;
    }

    /** Enregistre l Application ID + la cle privee .pem de l utilisateur. */
    @PostMapping("/config")
    public StatutBanqueDto enregistrerConfig(
            @RequestParam("applicationId") String applicationId,
            @RequestParam("clePrivee") MultipartFile clePrivee) throws Exception {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("Application ID manquant");
        }
        if (clePrivee == null || clePrivee.isEmpty()) {
            throw new IllegalArgumentException("Fichier de cle privee manquant");
        }
        String pem = new String(clePrivee.getBytes(), StandardCharsets.UTF_8);
        enableBankingService.enregistrerConfig(utilisateurCourant.recuperer(), applicationId, pem);
        return enableBankingService.recupererStatut(utilisateurCourant.recuperer());
    }

    @GetMapping("/banques")
    public List<BanqueDto> listerBanques(@RequestParam(defaultValue = "FR") String pays) {
        return enableBankingService.listerBanques(utilisateurCourant.recuperer(),
            pays.toUpperCase());
    }

    @PostMapping("/choisir")
    public StatutBanqueDto choisirBanque(@RequestBody Map<String, String> corps) {
        String nom = corps.get("nom");
        String pays = corps.get("pays");
        if (nom == null || pays == null) {
            throw new IllegalArgumentException("Nom ou pays de la banque manquant");
        }
        enableBankingService.choisirBanque(utilisateurCourant.recuperer(), nom, pays);
        return enableBankingService.recupererStatut(utilisateurCourant.recuperer());
    }

    /** Renvoie l URL de la page d authentification de la banque. */
    @GetMapping("/connexion")
    public Map<String, String> demarrerConnexion() {
        return Map.of("url",
            enableBankingService.demarrerAutorisation(utilisateurCourant.recuperer()));
    }

    /** Appele par le front au retour de la banque avec code + state. */
    @PostMapping("/callback")
    public StatutBanqueDto finaliserConnexion(@RequestBody Map<String, String> corps) {
        String code = corps.get("code");
        String state = corps.get("state");
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Code d autorisation manquant");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalStateException("State manquant");
        }
        enableBankingService.finaliserConnexion(utilisateurCourant.recuperer(), code, state);
        return enableBankingService.recupererStatut(utilisateurCourant.recuperer());
    }

    /** Historique des transactions bancaires sur N jours (pour Import). */
    @GetMapping("/transactions")
    public List<TransactionBancaireDto> listerTransactions(
            @RequestParam(defaultValue = "30") int jours) {
        return enableBankingService.listerTransactionsBancaires(
            utilisateurCourant.recuperer(), jours);
    }

    /** Recupere le solde bancaire et met a jour le compte local. */
    @PostMapping("/synchroniser")
    public Compte synchroniser() {
        var utilisateur = utilisateurCourant.recuperer();
        Compte compte = enableBankingService.synchroniser(utilisateur);
        historique.capturer(utilisateur);
        return compte;
    }
}
