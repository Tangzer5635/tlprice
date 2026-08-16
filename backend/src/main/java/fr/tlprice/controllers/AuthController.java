package fr.tlprice.controllers;

import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.UtilisateurRepository;
import fr.tlprice.services.SoldeService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record InscriptionDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "8 caracteres minimum") String motDePasse) {}

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurCourantService utilisateurCourant;
    private final SoldeService soldeService;
    private final PasswordEncoder encodeur;

    public AuthController(UtilisateurRepository utilisateurRepository,
                          UtilisateurCourantService utilisateurCourant,
                          SoldeService soldeService,
                          PasswordEncoder encodeur) {
        this.utilisateurRepository = utilisateurRepository;
        this.utilisateurCourant = utilisateurCourant;
        this.soldeService = soldeService;
        this.encodeur = encodeur;
    }

    @PostMapping("/inscription")
    public Map<String, String> inscrire(@RequestBody @jakarta.validation.Valid InscriptionDto dto) {
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
}
