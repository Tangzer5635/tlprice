package fr.tlprice.controllers;

import fr.tlprice.dto.FiabiliteDto;
import fr.tlprice.dto.PointHistoriqueDto;
import fr.tlprice.services.HistoriqueSoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historique")
public class HistoriqueController {

    private final HistoriqueSoldeService historique;
    private final UtilisateurCourantService utilisateurCourant;

    public HistoriqueController(HistoriqueSoldeService historique,
                                UtilisateurCourantService utilisateurCourant) {
        this.historique = historique;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public List<PointHistoriqueDto> lire(@RequestParam(defaultValue = "60") int jours) {
        return historique.lire(utilisateurCourant.recuperer(), jours);
    }

    @GetMapping("/fiabilite")
    public FiabiliteDto fiabilite() {
        return historique.fiabilite(utilisateurCourant.recuperer());
    }
}
