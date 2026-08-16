package fr.tlprice.controllers;

import fr.tlprice.dto.CapaciteEpargneDto;
import fr.tlprice.dto.SoldeProjeteDto;
import fr.tlprice.entities.Compte;
import fr.tlprice.services.HistoriqueSoldeService;
import fr.tlprice.services.SoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/solde")
public class SoldeController {

    private final SoldeService soldeService;
    private final HistoriqueSoldeService historique;
    private final UtilisateurCourantService utilisateurCourant;

    public SoldeController(SoldeService soldeService,
                           HistoriqueSoldeService historique,
                           UtilisateurCourantService utilisateurCourant) {
        this.soldeService = soldeService;
        this.historique = historique;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public Compte recuperer() {
        return soldeService.recupererCompte(utilisateurCourant.recuperer());
    }

    /** Mise a jour manuelle du solde (utile sans connexion bancaire). */
    @PutMapping
    public Compte mettreAJour(@RequestBody Map<String, BigDecimal> corps) {
        BigDecimal solde = corps.get("solde");
        if (solde == null) {
            throw new IllegalArgumentException("Champ solde manquant");
        }
        var utilisateur = utilisateurCourant.recuperer();
        Compte compte = soldeService.mettreAJourSolde(utilisateur, solde);
        historique.capturer(utilisateur);
        return compte;
    }

    /**
     * @param scenario hypothese a superposer, ou absent pour la projection reelle
     */
    @GetMapping("/projete")
    public SoldeProjeteDto projete(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long scenario) {
        return soldeService.calculerSoldeProjete(
            utilisateurCourant.recuperer(), date, scenario);
    }

    /**
     * Combien on peut virer ailleurs d ici l horizon sans passer sous son
     * seuil. Sans date, on regarde les 30 prochains jours.
     */
    @GetMapping("/capacite-epargne")
    public CapaciteEpargneDto capaciteEpargne(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return soldeService.capaciteEpargne(utilisateurCourant.recuperer(), date);
    }
}
