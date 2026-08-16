package fr.tlprice.controllers;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.RegleRenommage;
import fr.tlprice.services.RegleRenommageService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regles-renommage")
public class RegleRenommageController {

    public record ApprentissageDto(
        @NotBlank String libelleOriginal,
        @NotBlank String libelleFinal,
        @NotNull Categorie categorie) {}

    private final RegleRenommageService service;
    private final UtilisateurCourantService utilisateurCourant;

    public RegleRenommageController(RegleRenommageService service,
                                    UtilisateurCourantService utilisateurCourant) {
        this.service = service;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public List<RegleRenommage> lister() {
        return service.lister(utilisateurCourant.recuperer());
    }

    @PostMapping
    public RegleRenommage apprendre(@RequestBody @jakarta.validation.Valid ApprentissageDto dto) {
        return service.apprendre(utilisateurCourant.recuperer(),
            dto.libelleOriginal(), dto.libelleFinal(), dto.categorie());
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> supprimer(@PathVariable Long id) {
        service.supprimer(id, utilisateurCourant.recuperer());
        return Map.of("supprime", true);
    }
}
