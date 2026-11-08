package fr.tlprice.controllers;

import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.UtilisateurRepository;
import fr.tlprice.services.SoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record InscriptionDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 200, message = "8 caracteres minimum") String motDePasse) {}

    public record ChangementMotDePasseDto(
        @NotBlank String actuel,
        @NotBlank @Size(min = 8, max = 200, message = "8 caracteres minimum") String nouveau) {}

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurCourantService utilisateurCourant;
    private final SoldeService soldeService;
    private final PasswordEncoder encodeur;
    private final boolean inscriptionOuverte;

    public AuthController(UtilisateurRepository utilisateurRepository,
                          UtilisateurCourantService utilisateurCourant,
                          SoldeService soldeService,
                          PasswordEncoder encodeur,
                          @Value("${tlprice.inscription.ouverte:false}") boolean inscriptionOuverte) {
        this.utilisateurRepository = utilisateurRepository;
        this.utilisateurCourant = utilisateurCourant;
        this.soldeService = soldeService;
        this.encodeur = encodeur;
        this.inscriptionOuverte = inscriptionOuverte;
    }

    /**
     * L application est publique sur Internet : n importe qui pouvait se
     * creer un compte sur ton Raspberry Pi. L inscription est desormais
     * fermee par defaut, sauf tant qu aucun compte n existe (premiere
     * installation). TLPRICE_INSCRIPTION_OUVERTE=true la rouvre, le temps
     * d inviter quelqu un.
     */
    private boolean inscriptionPossible() {
        return inscriptionOuverte || utilisateurRepository.count() == 0;
    }

    /** Ce que l ecran de connexion doit afficher, avant toute authentification. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("inscriptionOuverte", inscriptionPossible());
    }

    @PostMapping("/inscription")
    public Map<String, String> inscrire(@RequestBody @Valid InscriptionDto dto) {
        if (!inscriptionPossible()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Les inscriptions sont fermees sur ce serveur.");
        }
        String email = dto.email().toLowerCase().trim();
        if (utilisateurRepository.existsByEmail(email)) {
            throw new IllegalStateException("Un compte existe deja avec cet email.");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setMotDePasseHash(encodeur.encode(dto.motDePasse()));
        utilisateurRepository.save(utilisateur);
        soldeService.recupererCompte(utilisateur);
        return Map.of("email", email);
    }

    /** Renvoie l utilisateur connecte, ou 401 si personne. */
    @GetMapping("/moi")
    public Map<String, String> moi(Authentication auth) {
        if (auth == null) {
            throw new org.springframework.security.authentication
                .InsufficientAuthenticationException("Non connecte");
        }
        return Map.of("email", utilisateurCourant.recuperer().getEmail());
    }

    /**
     * Change le mot de passe. Le cookie de connexion persistant est signe
     * avec le hash du mot de passe : tous les AUTRES appareils seront donc
     * deconnectes, ce qui est exactement ce qu on attend apres une fuite.
     */
    @PostMapping("/mot-de-passe")
    public Map<String, Boolean> changerMotDePasse(@RequestBody @Valid ChangementMotDePasseDto dto) {
        Utilisateur utilisateur = utilisateurCourant.recuperer();
        if (!encodeur.matches(dto.actuel(), utilisateur.getMotDePasseHash())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect.");
        }
        utilisateur.setMotDePasseHash(encodeur.encode(dto.nouveau()));
        utilisateurRepository.save(utilisateur);
        return Map.of("change", true);
    }
}
