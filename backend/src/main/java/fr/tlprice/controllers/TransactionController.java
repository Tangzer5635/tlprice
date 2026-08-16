package fr.tlprice.controllers;

import fr.tlprice.entities.Transaction;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.TransactionRepository;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository repository;
    private final UtilisateurCourantService utilisateurCourant;

    public TransactionController(TransactionRepository repository,
                                 UtilisateurCourantService utilisateurCourant) {
        this.repository = repository;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public List<Transaction> lister() {
        return repository.findByUtilisateurOrderByDateOperationDesc(utilisateurCourant.recuperer());
    }

    @PostMapping
    public Transaction creer(@RequestBody @Valid Transaction transaction) {
        transaction.setId(null);
        transaction.setUtilisateur(utilisateurCourant.recuperer());
        return repository.save(transaction);
    }

    /** Bascule le flag debiteeEnBanque. */
    @PatchMapping("/{id}/debitee")
    public Transaction basculerDebitee(@PathVariable Long id) {
        Utilisateur utilisateur = utilisateurCourant.recuperer();
        Transaction transaction = repository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Transaction introuvable"));
        transaction.setDebiteeEnBanque(!transaction.isDebiteeEnBanque());
        return repository.save(transaction);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> supprimer(@PathVariable Long id) {
        Utilisateur utilisateur = utilisateurCourant.recuperer();
        Transaction transaction = repository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Transaction introuvable"));
        repository.delete(transaction);
        return Map.of("supprime", true);
    }

    /** Renomme uniquement le libelle. */
    @PatchMapping("/{id}/libelle")
    public Transaction renommer(@PathVariable Long id,
                                @RequestBody Map<String, String> corps) {
        String nouveau = corps.get("libelle");
        if (nouveau == null || nouveau.isBlank()) {
            throw new IllegalArgumentException("Libelle manquant");
        }
        Transaction tr = repository.findByIdAndUtilisateur(id, utilisateurCourant.recuperer())
            .orElseThrow(() -> new IllegalStateException("Transaction introuvable"));
        tr.setLibelle(nouveau.trim());
        return repository.save(tr);
    }
}
