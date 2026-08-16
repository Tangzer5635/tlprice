package fr.tlprice.services;

import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.RegleRenommage;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.RegleRenommageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RegleRenommageService {

    private final RegleRenommageRepository repository;

    public RegleRenommageService(RegleRenommageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RegleRenommage> lister(Utilisateur utilisateur) {
        return repository.findByUtilisateurOrderByLibelleFinalAsc(utilisateur);
    }

    @Transactional
    public void supprimer(Long id, Utilisateur utilisateur) {
        RegleRenommage r = repository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Regle introuvable"));
        repository.delete(r);
    }

    /** Cherche une regle qui matche le libelle bancaire brut. */
    @Transactional(readOnly = true)
    public Optional<RegleRenommage> appliquer(Utilisateur utilisateur, String libelleBrut) {
        for (RegleRenommage r : lister(utilisateur)) {
            if (NormalisationLibelle.matche(r.getMotif(), libelleBrut)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /**
     * Apprend une regle a partir d un renommage. Si une regle avec le meme
     * motif existe deja on met a jour ses valeurs plutot que de doublonner.
     */
    @Transactional
    public RegleRenommage apprendre(Utilisateur utilisateur, String libelleOriginal,
                                    String libelleFinal, Categorie categorie) {
        String motif = NormalisationLibelle.motifDe(libelleOriginal);
        if (motif.isBlank()) {
            throw new IllegalStateException(
                "Le libelle bancaire est trop generique pour en tirer une regle.");
        }
        RegleRenommage regle = lister(utilisateur).stream()
            .filter(r -> r.getMotif().equals(motif))
            .findFirst()
            .orElseGet(() -> {
                RegleRenommage r = new RegleRenommage();
                r.setUtilisateur(utilisateur);
                r.setMotif(motif);
                return r;
            });
        regle.setLibelleFinal(libelleFinal);
        regle.setCategorie(categorie);
        return repository.save(regle);
    }
}
