package fr.tlprice.controllers;

import fr.tlprice.dto.ScenarioDto;
import fr.tlprice.entities.LigneScenario;
import fr.tlprice.entities.Scenario;
import fr.tlprice.services.ScenarioService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final UtilisateurCourantService utilisateurCourant;

    public ScenarioController(ScenarioService scenarios,
                              UtilisateurCourantService utilisateurCourant) {
        this.scenarios = scenarios;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public List<ScenarioDto> lister() {
        List<ScenarioDto> sortie = new ArrayList<>();
        for (Scenario s : scenarios.lister(utilisateurCourant.recuperer())) {
            sortie.add(versDto(s));
        }
        return sortie;
    }

    @PostMapping
    public ScenarioDto creer(@RequestBody @Valid ScenarioDto dto) {
        return versDto(scenarios.creer(utilisateurCourant.recuperer(), dto));
    }

    @PutMapping("/{id}")
    public ScenarioDto modifier(@PathVariable Long id, @RequestBody @Valid ScenarioDto dto) {
        return versDto(scenarios.modifier(utilisateurCourant.recuperer(), id, dto));
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> supprimer(@PathVariable Long id) {
        scenarios.supprimer(utilisateurCourant.recuperer(), id);
        return Map.of("supprime", true);
    }

    private ScenarioDto versDto(Scenario s) {
        ScenarioDto dto = new ScenarioDto();
        dto.setId(s.getId());
        dto.setNom(s.getNom());
        List<ScenarioDto.LigneDto> lignes = new ArrayList<>();
        for (LigneScenario l : s.getLignes()) {
            ScenarioDto.LigneDto d = new ScenarioDto.LigneDto();
            d.setId(l.getId());
            d.setLibelle(l.getLibelle());
            d.setMontant(l.getMontant());
            d.setType(l.getType());
            d.setCategorie(l.getCategorie());
            d.setDate(l.getDate());
            lignes.add(d);
        }
        lignes.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        dto.setLignes(lignes);
        return dto;
    }
}
