package fr.tlprice.services;

import fr.tlprice.dto.ScenarioDto;
import fr.tlprice.entities.LigneScenario;
import fr.tlprice.entities.Scenario;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creation et modification des hypotheses de projection. */
@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;

    public ScenarioService(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Scenario> lister(Utilisateur utilisateur) {
        return scenarioRepository.findByUtilisateurOrderByDateCreationDesc(utilisateur);
    }

    @Transactional(readOnly = true)
    public Scenario obligatoire(Utilisateur utilisateur, Long id) {
        return scenarioRepository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Scenario introuvable."));
    }

    @Transactional
    public Scenario creer(Utilisateur utilisateur, ScenarioDto dto) {
        Scenario s = new Scenario();
        s.setUtilisateur(utilisateur);
        appliquer(s, dto);
        return scenarioRepository.save(s);
    }

    @Transactional
    public Scenario modifier(Utilisateur utilisateur, Long id, ScenarioDto dto) {
        Scenario s = obligatoire(utilisateur, id);
        appliquer(s, dto);
        return scenarioRepository.save(s);
    }

    @Transactional
    public void supprimer(Utilisateur utilisateur, Long id) {
        scenarioRepository.delete(obligatoire(utilisateur, id));
    }

    /**
     * Les lignes sont remplacees en bloc plutot que reconciliees une a une.
     *
     * Un scenario compte quelques lignes et se modifie a la main dans un
     * formulaire : reconcilier par identifiant couterait de la complexite
     * pour aucun gain. orphanRemoval se charge de la suppression des
     * anciennes.
     */
    private void appliquer(Scenario s, ScenarioDto dto) {
        s.setNom(dto.getNom().trim());
        s.getLignes().clear();
        if (dto.getLignes() == null) return;
        for (ScenarioDto.LigneDto l : dto.getLignes()) {
            LigneScenario ligne = new LigneScenario();
            ligne.setScenario(s);
            ligne.setLibelle(l.getLibelle().trim());
            ligne.setMontant(l.getMontant().abs());
            ligne.setType(l.getType());
            ligne.setCategorie(l.getCategorie());
            ligne.setDate(l.getDate());
            s.getLignes().add(ligne);
        }
    }
}
