package fr.tlprice.controllers;

import fr.tlprice.dto.AbonnementPushDto;
import fr.tlprice.entities.AbonnementPush;
import fr.tlprice.services.NotificationService;
import fr.tlprice.services.PushService;
import fr.tlprice.services.UtilisateurCourantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;
    private final NotificationService notifications;
    private final UtilisateurCourantService utilisateurCourant;

    public PushController(PushService push,
                          NotificationService notifications,
                          UtilisateurCourantService utilisateurCourant) {
        this.push = push;
        this.notifications = notifications;
        this.utilisateurCourant = utilisateurCourant;
    }

    /** Le navigateur en a besoin pour s abonner (applicationServerKey). */
    @GetMapping("/cle-publique")
    public Map<String, Object> clePublique() {
        return Map.of(
            "disponible", push.disponible(),
            "cle", push.disponible() ? push.clePublique() : "");
    }

    @GetMapping("/appareils")
    public List<Map<String, Object>> appareils() {
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (AbonnementPush a : push.appareils(utilisateurCourant.recuperer())) {
            Map<String, Object> ligne = new java.util.LinkedHashMap<>();
            ligne.put("id", a.getId());
            ligne.put("appareil", a.getAppareil() == null ? "Appareil" : a.getAppareil());
            ligne.put("endpoint", a.getEndpoint());
            ligne.put("dateCreation", a.getDateCreation());
            // null = suit l heure globale du compte
            ligne.put("heureRecap", a.getHeureRecap());
            sortie.add(ligne);
        }
        return sortie;
    }

    /**
     * Regle l heure de recap propre a un appareil.
     *
     * heure = null (champ absent) remet l appareil sur l heure globale du
     * compte, ce qui est different de "0h" — d ou le type Integer.
     */
    @PatchMapping("/appareils/{id}/heure")
    public Map<String, Object> reglerHeure(@PathVariable Long id,
                                           @RequestBody Map<String, Object> corps) {
        Object brut = corps.get("heure");
        Integer heure = null;
        if (brut != null) {
            heure = ((Number) brut).intValue();
            if (heure < 0 || heure > 23) {
                throw new IllegalArgumentException("L heure doit etre comprise entre 0 et 23.");
            }
        }
        push.reglerHeure(utilisateurCourant.recuperer(), id, heure);
        Map<String, Object> reponse = new java.util.LinkedHashMap<>();
        reponse.put("heureRecap", heure);
        return reponse;
    }

    /** Renomme un appareil. */
    @PatchMapping("/appareils/{id}/nom")
    public Map<String, Object> renommer(@PathVariable Long id,
                                        @RequestBody Map<String, String> corps) {
        push.renommer(utilisateurCourant.recuperer(), id, corps.get("nom"));
        return Map.of("renomme", true);
    }

    /** Deconnecte un appareil du compte, meme si on ne l a pas sous la main. */
    @DeleteMapping("/appareils/{id}")
    public Map<String, Object> retirer(@PathVariable Long id) {
        push.supprimerParId(utilisateurCourant.recuperer(), id);
        return Map.of("retire", true);
    }

    @PostMapping("/abonnement")
    public Map<String, Object> abonner(@RequestBody @Valid AbonnementPushDto dto) {
        push.enregistrer(utilisateurCourant.recuperer(), dto);
        return Map.of("abonne", true);
    }

    @DeleteMapping("/abonnement")
    public Map<String, Object> desabonner(@RequestBody Map<String, String> corps) {
        String endpoint = corps.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Champ endpoint manquant");
        }
        push.supprimer(utilisateurCourant.recuperer(), endpoint);
        return Map.of("abonne", false);
    }

    /** Bouton "envoyer un test" des reglages. */
    @PostMapping("/test")
    public Map<String, String> tester() {
        return Map.of("statut", notifications.envoyerPushTest(utilisateurCourant.recuperer()));
    }
}
