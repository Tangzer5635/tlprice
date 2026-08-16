package fr.tlprice.controllers;

import fr.tlprice.dto.FichierSauvegardeDto;
import fr.tlprice.dto.RapportImportDto;
import fr.tlprice.dto.SauvegardeDto;
import fr.tlprice.services.SauvegardeAutoService;
import fr.tlprice.services.SauvegardeService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Deux usages distincts derriere la meme famille d URL.
 *
 * Le transfert d un compte entre deux instances : on telecharge un JSON
 * depuis l instance source, on le depose sur l instance cible.
 *
 * Et les sauvegardes automatiques ecrites chaque nuit sur le serveur, qu on
 * peut lister et rapatrier — parce qu une sauvegarde qui ne quitte jamais la
 * carte SD qu elle protege ne protege de pas grand chose.
 */
@RestController
@RequestMapping("/api/donnees")
public class SauvegardeController {

    private final SauvegardeService sauvegardeService;
    private final SauvegardeAutoService sauvegardeAutoService;
    private final UtilisateurCourantService utilisateurCourant;

    public SauvegardeController(SauvegardeService sauvegardeService,
                                SauvegardeAutoService sauvegardeAutoService,
                                UtilisateurCourantService utilisateurCourant) {
        this.sauvegardeService = sauvegardeService;
        this.sauvegardeAutoService = sauvegardeAutoService;
        this.utilisateurCourant = utilisateurCourant;
    }

    // ==================================================================
    // Transfert manuel
    // ==================================================================

    /** Telecharge toutes les donnees saisies du compte connecte. */
    @GetMapping("/export")
    public ResponseEntity<SauvegardeDto> exporter() {
        SauvegardeDto sauvegarde = sauvegardeService.exporter(utilisateurCourant.recuperer());
        String nomFichier = "tlprice-sauvegarde-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + nomFichier + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(sauvegarde);
    }

    /**
     * Reinjecte une sauvegarde dans le compte connecte.
     *
     * @param mode        "fusion" (defaut, sans doublon) ou "remplacement"
     * @param solde       reprendre aussi le solde bancaire du fichier
     * @param parametres  reprendre aussi les preferences de notification
     */
    @PostMapping("/import")
    public RapportImportDto importer(
            @RequestBody SauvegardeDto sauvegarde,
            @RequestParam(defaultValue = "fusion") String mode,
            @RequestParam(defaultValue = "false") boolean solde,
            @RequestParam(defaultValue = "false") boolean parametres) {

        boolean remplacer = "remplacement".equalsIgnoreCase(mode);
        return sauvegardeService.importer(
            utilisateurCourant.recuperer(), sauvegarde, remplacer, solde, parametres);
    }

    // ==================================================================
    // Sauvegardes automatiques
    // ==================================================================

    /** Les sauvegardes nocturnes disponibles, de la plus recente a la plus ancienne. */
    @GetMapping("/sauvegardes")
    public List<FichierSauvegardeDto> sauvegardes() {
        return sauvegardeAutoService.lister(utilisateurCourant.recuperer());
    }

    /**
     * Declenche une sauvegarde immediate, sans attendre 3h15. Utile avant une
     * manipulation risquee, et pour verifier que le mecanisme fonctionne
     * plutot que de le decouvrir le jour ou on en a besoin.
     */
    @PostMapping("/sauvegardes")
    public Map<String, String> sauvegarderMaintenant() {
        try {
            Path ecrit = sauvegardeAutoService.ecrire(utilisateurCourant.recuperer());
            return Map.of("nom", ecrit.getFileName().toString());
        } catch (IOException e) {
            // Disque plein, volume en lecture seule : un 500 muet ne dirait
            // rien, alors que c est exactement le genre de panne qu il faut
            // voir tout de suite.
            throw new IllegalStateException(
                "Ecriture de la sauvegarde impossible : " + e.getMessage());
        }
    }

    /** Rapatrie une sauvegarde du serveur vers l appareil. */
    @GetMapping("/sauvegardes/telecharger")
    public ResponseEntity<Resource> telechargerSauvegarde(@RequestParam String nom) {
        Path fichier = sauvegardeAutoService.fichier(utilisateurCourant.recuperer(), nom);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fichier.getFileName() + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new FileSystemResource(fichier));
    }
}
