package fr.tlprice.services;

import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.UtilisateurRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Recupere l utilisateur connecte depuis le contexte Spring Security. */
@Service
public class UtilisateurCourantService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurCourantService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    public Utilisateur recuperer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Aucun utilisateur connecte");
        }
        return utilisateurRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));
    }
}
