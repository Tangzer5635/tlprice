package fr.tlprice.controllers;

import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.services.ExceptionEcheanceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import fr.tlprice.services.OperationService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private final OperationRecurrenteRepository repository;
    private final UtilisateurCourantService utilisateurCourant;
    private final ExceptionEcheanceService exceptionService;
    private final OperationService operationService;

    public OperationController(OperationRecurrenteRepository repository,
                               UtilisateurCourantService utilisateurCourant,
                               ExceptionEcheanceService exceptionService,
                               OperationService operationService) {
        this.repository = repository;
        this.utilisateurCourant = utilisateurCourant;
        this.exceptionService = exceptionService;
        this.operationService = operationService;
    }

    @GetMapping
    public List<OperationRecurrente> lister() {
        return repository.findByUtilisateurOrderByLibelleAsc(utilisateurCourant.recuperer());
    }

    @PostMapping
    public OperationRecurrente creer(@RequestBody @Valid OperationRecurrente operation) {
        operation.setId(null);
        operation.setUtilisateur(utilisateurCourant.recuperer());
        return repository.save(operation);
    }

    @PutMapping("/{id}")
    public OperationRecurrente modifier(@PathVariable Long id,
                                        @RequestBody @Valid OperationRecurrente modif) {
        Utilisateur utilisateur = utilisateurCourant.recuperer();
        OperationRecurrente operation = repository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Operation introuvable"));
        operation.setLibelle(modif.getLibelle());
        operation.setMontant(modif.getMontant());
        operation.setType(modif.getType());
        operation.setFrequence(modif.getFrequence());
        operation.setJourDuMois(modif.getJourDuMois());
        operation.setCategorie(modif.getCategorie());
        operation.setDateDebut(modif.getDateDebut());
        operation.setDateFin(modif.getDateFin());
        operation.setActive(modif.isActive());
        return repository.save(operation);
    }

    /** Ce que la suppression emportera, pour prevenir avant d agir. */
    @GetMapping("/{id}/impact")
    public Map<String, Object> impact(@PathVariable Long id) {
        return operationService.impactSuppression(utilisateurCourant.recuperer(), id);
    }

    /**
     * Supprime l operation et ses dependances. Passe par le service : deux
     * tables la referencent par cle etrangere, la suppression directe echoue.
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> supprimer(@PathVariable Long id) {
        return operationService.supprimer(utilisateurCourant.recuperer(), id);
    }

    /** Renomme uniquement le libelle d une operation existante. */
    @PatchMapping("/{id}/libelle")
    public OperationRecurrente renommer(@PathVariable Long id,
                                        @RequestBody Map<String, String> corps) {
        String nouveau = corps.get("libelle");
        if (nouveau == null || nouveau.isBlank()) {
            throw new IllegalArgumentException("Libelle manquant");
        }
        OperationRecurrente op = repository.findByIdAndUtilisateur(id, utilisateurCourant.recuperer())
            .orElseThrow(() -> new IllegalStateException("Operation introuvable"));
        op.setLibelle(nouveau.trim());
        return repository.save(op);
    }

    /** Ajuste ou annule une occurrence pour une date donnee. */
    @PostMapping("/{id}/exceptions")
    public ExceptionEcheance ajuster(@PathVariable Long id,
                                     @RequestBody Map<String, Object> corps) {
        String dateStr = (String) corps.get("date");
        if (dateStr == null) throw new IllegalArgumentException("date manquante");
        LocalDate date = LocalDate.parse(dateStr);
        Object montantBrut = corps.get("montantOverride");
        BigDecimal montant = montantBrut == null ? null : new BigDecimal(montantBrut.toString());
        boolean annulee = Boolean.TRUE.equals(corps.get("annulee"));
        return exceptionService.ajusterOuAnnuler(
            utilisateurCourant.recuperer(), id, date, montant, annulee);
    }

    /** Reinitialise l occurrence a sa valeur par defaut. */
    @DeleteMapping("/{id}/exceptions")
    public Map<String, Boolean> reinitialiser(@PathVariable Long id,
                                              @RequestParam String date) {
        exceptionService.reinitialiser(
            utilisateurCourant.recuperer(), id, LocalDate.parse(date));
        return Map.of("reinitialise", true);
    }
}
