package fr.tlprice.controllers;

import fr.tlprice.dto.LigneExportDto;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.services.ExportService;
import fr.tlprice.services.UtilisateurCourantService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Telechargement des mouvements d une periode au format CSV ou Excel.
 * Exemple : GET /api/export?debut=2026-07-01&fin=2026-07-31&format=xlsx
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final String TYPE_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService;
    private final UtilisateurCourantService utilisateurCourant;

    public ExportController(ExportService exportService,
                            UtilisateurCourantService utilisateurCourant) {
        this.exportService = exportService;
        this.utilisateurCourant = utilisateurCourant;
    }

    @GetMapping
    public ResponseEntity<byte[]> exporter(
            @RequestParam String debut,
            @RequestParam String fin,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(name = "echeances", defaultValue = "true") boolean inclureEcheances) {

        LocalDate dateDebut;
        LocalDate dateFin;
        try {
            dateDebut = LocalDate.parse(debut);
            dateFin = LocalDate.parse(fin);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates invalides, format attendu AAAA-MM-JJ.");
        }
        if (dateDebut.isAfter(dateFin)) {
            throw new IllegalArgumentException("La date de debut doit preceder la date de fin.");
        }

        Utilisateur utilisateur = utilisateurCourant.recuperer();
        List<LigneExportDto> lignes =
            exportService.collecter(utilisateur, dateDebut, dateFin, inclureEcheances);

        boolean excel = "xlsx".equalsIgnoreCase(format);
        byte[] contenu = excel
            ? exportService.versXlsx(lignes, dateDebut, dateFin)
            : exportService.versCsv(lignes);

        String nomFichier = "tlprice-" + dateDebut + "_" + dateFin + (excel ? ".xlsx" : ".csv");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomFichier + "\"")
            .contentType(MediaType.parseMediaType(
                excel ? TYPE_XLSX : "text/csv;charset=UTF-8"))
            .body(contenu);
    }
}
