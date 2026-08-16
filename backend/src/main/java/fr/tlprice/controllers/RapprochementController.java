package fr.tlprice.controllers;

import fr.tlprice.dto.RapprochementDto;
import fr.tlprice.services.RapprochementService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rapprochement")
public class RapprochementController {

    private final RapprochementService rapprochement;
    private final UtilisateurCourantService utilisateurCourant;

    public RapprochementController(RapprochementService rapprochement,
                                   UtilisateurCourantService utilisateurCourant) {
        this.rapprochement = rapprochement;
        this.utilisateurCourant = utilisateurCourant;
    }

    /** Lecture seule : ce qui a ete detecte lors de la derniere analyse. */
    @GetMapping
    public List<RapprochementDto> lister(@RequestParam(defaultValue = "false") boolean tout) {
        return rapprochement.lister(utilisateurCourant.recuperer(), tout);
    }

    /** Relance l analyse (appel banque) puis renvoie la liste a jour. */
    @PostMapping("/analyser")
    public List<RapprochementDto> analyser(@RequestParam(defaultValue = "45") int jours) {
        var utilisateur = utilisateurCourant.recuperer();
        rapprochement.analyser(utilisateur, jours);
        return rapprochement.lister(utilisateur, false);
    }

    /** Pastille du tableau de bord. */
    @GetMapping("/en-attente")
    public Map<String, Long> enAttente() {
        return Map.of("nombre", rapprochement.nombreEnAttente(utilisateurCourant.recuperer()));
    }

    @PostMapping("/{id}/ponctuel")
    public Map<String, Object> ponctuel(@PathVariable Long id) {
        return reponse(rapprochement.corrigerPonctuellement(utilisateurCourant.recuperer(), id));
    }

    @PostMapping("/{id}/definitif")
    public Map<String, Object> definitif(@PathVariable Long id) {
        return reponse(rapprochement.corrigerDefinitivement(utilisateurCourant.recuperer(), id));
    }

    /**
     * La correction a peut-etre appris une regle de renommage au passage. On
     * le dit au client plutot que de le faire en silence : une regle creee a
     * partir d un appariement PROBABLE peut etre fausse, et l utilisateur
     * doit pouvoir aller la retirer dans l ecran Regles.
     */
    private Map<String, Object> reponse(String regleApprise) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("traite", true);
        if (regleApprise != null) corps.put("regleApprise", regleApprise);
        return corps;
    }

    @PostMapping("/{id}/annuler-echeance")
    public Map<String, Boolean> annulerEcheance(@PathVariable Long id) {
        rapprochement.annulerEcheance(utilisateurCourant.recuperer(), id);
        return Map.of("traite", true);
    }

    @PostMapping("/{id}/ignorer")
    public Map<String, Boolean> ignorer(@PathVariable Long id) {
        rapprochement.ignorer(utilisateurCourant.recuperer(), id);
        return Map.of("traite", true);
    }
}
