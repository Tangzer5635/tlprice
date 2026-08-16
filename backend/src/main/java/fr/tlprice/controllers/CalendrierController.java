package fr.tlprice.controllers;

import fr.tlprice.dto.JourCalendrierDto;
import fr.tlprice.services.SoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/calendrier")
public class CalendrierController {

    private final SoldeService soldeService;
    private final UtilisateurCourantService utilisateurCourant;

    public CalendrierController(SoldeService soldeService,
                                UtilisateurCourantService utilisateurCourant) {
        this.soldeService = soldeService;
        this.utilisateurCourant = utilisateurCourant;
    }

    /** GET /api/calendrier/2026/07 */
    @GetMapping("/{annee}/{mois}")
    public List<JourCalendrierDto> mois(@PathVariable int annee, @PathVariable int mois) {
        return soldeService.calendrierMensuel(utilisateurCourant.recuperer(),
            YearMonth.of(annee, mois));
    }
}
