package fr.tlprice.services;

import fr.tlprice.dto.LigneExportDto;
import fr.tlprice.dto.MouvementDto;
import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.Transaction;
import fr.tlprice.entities.TypeOperation;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.TransactionRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construit les fichiers d export (CSV pour un usage rapide, XLSX mis en
 * forme pour Excel / LibreOffice). L export reprend les transactions
 * ponctuelles de la periode et, au choix, les echeances des operations
 * recurrentes generees sur cette meme periode.
 */
@Service
public class ExportService {

    private static final String[] ENTETES = {
        "Date", "Libelle", "Categorie", "Type", "Montant", "Origine", "Statut"
    };

    /** Separateur point-virgule : c est ce qu attend Excel en configuration FR. */
    private static final char SEPARATEUR = ';';

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final EcheanceService echeanceService;

    public ExportService(TransactionRepository transactionRepository,
                         EcheanceService echeanceService) {
        this.transactionRepository = transactionRepository;
        this.echeanceService = echeanceService;
    }

    // ------------------------------------------------------------------
    // Collecte
    // ------------------------------------------------------------------

    /** Rassemble transactions et echeances de la periode, triees par date. */
    @Transactional(readOnly = true)
    public List<LigneExportDto> collecter(Utilisateur utilisateur, LocalDate debut,
                                          LocalDate fin, boolean inclureEcheances) {
        List<LigneExportDto> lignes = new ArrayList<>();

        List<Transaction> transactions = transactionRepository
            .findByUtilisateurAndDateOperationBetweenOrderByDateOperationAsc(utilisateur, debut, fin);
        for (Transaction t : transactions) {
            lignes.add(new LigneExportDto(
                t.getDateOperation(),
                t.getLibelle(),
                t.getCategorie(),
                t.getType(),
                t.getMontant(),
                "Ponctuel",
                t.isDebiteeEnBanque() ? "Debitee en banque" : "En attente"));
        }

        if (inclureEcheances) {
            for (MouvementDto m : echeanceService.genererEcheances(utilisateur, debut, fin)) {
                lignes.add(new LigneExportDto(
                    m.getDate(),
                    m.getLibelle(),
                    m.getCategorie(),
                    m.getType(),
                    m.getMontant(),
                    "Recurrent",
                    m.getMontantOriginal() != null ? "Echeance ajustee" : "Echeance prevue"));
            }
        }

        Comparator<LigneExportDto> parDate = Comparator.comparing(LigneExportDto::getDate);
        lignes.sort(parDate.thenComparing(LigneExportDto::getLibelle));
        return lignes;
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    /**
     * CSV UTF-8 avec BOM (sans lui Excel massacre les accents) et separateur
     * point-virgule. Les montants utilisent la virgule decimale francaise.
     */
    public byte[] versCsv(List<LigneExportDto> lignes) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');

        for (int i = 0; i < ENTETES.length; i++) {
            if (i > 0) sb.append(SEPARATEUR);
            sb.append(ENTETES[i]);
        }
        sb.append('\n');

        for (LigneExportDto ligne : lignes) {
            sb.append(ligne.getDate().format(DATE_FR)).append(SEPARATEUR);
            sb.append(echapper(ligne.getLibelle())).append(SEPARATEUR);
            sb.append(libelleCategorie(ligne.getCategorie())).append(SEPARATEUR);
            sb.append(ligne.getType() == TypeOperation.DEBIT ? "Debit" : "Credit").append(SEPARATEUR);
            sb.append(montantFrancais(ligne.getMontantSigne())).append(SEPARATEUR);
            sb.append(ligne.getOrigine()).append(SEPARATEUR);
            sb.append(echapper(ligne.getStatut()));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Entoure de guillemets si la valeur contient un separateur ou un guillemet. */
    private String echapper(String valeur) {
        if (valeur == null) return "";
        boolean besoin = valeur.indexOf(SEPARATEUR) >= 0
            || valeur.indexOf('"') >= 0
            || valeur.indexOf('\n') >= 0
            || valeur.indexOf('\r') >= 0;
        if (!besoin) return valeur;
        return '"' + valeur.replace("\"", "\"\"") + '"';
    }

    private String montantFrancais(BigDecimal montant) {
        return montant.setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------------------
    // Excel
    // ------------------------------------------------------------------

    /**
     * Classeur a deux feuilles : le detail des mouvements et une synthese
     * par categorie. Les largeurs de colonnes sont fixees en dur plutot que
     * calculees (autoSizeColumn a besoin des polices systeme, absentes de
     * l image JRE slim qui tourne sur le Raspberry Pi).
     */
    public byte[] versXlsx(List<LigneExportDto> lignes, LocalDate debut, LocalDate fin) {
        try (XSSFWorkbook classeur = new XSSFWorkbook();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {

            CreationHelper aide = classeur.getCreationHelper();

            Font policeEntete = classeur.createFont();
            policeEntete.setBold(true);
            policeEntete.setColor(IndexedColors.WHITE.getIndex());

            CellStyle styleEntete = classeur.createCellStyle();
            styleEntete.setFont(policeEntete);
            styleEntete.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleEntete.setBorderBottom(BorderStyle.THIN);

            CellStyle styleDate = classeur.createCellStyle();
            styleDate.setDataFormat(aide.createDataFormat().getFormat("dd/mm/yyyy"));

            CellStyle styleMontant = classeur.createCellStyle();
            styleMontant.setDataFormat(aide.createDataFormat().getFormat("#,##0.00 \u20AC"));

            Font policeTotal = classeur.createFont();
            policeTotal.setBold(true);

            CellStyle styleTotal = classeur.createCellStyle();
            styleTotal.setFont(policeTotal);
            styleTotal.setDataFormat(aide.createDataFormat().getFormat("#,##0.00 \u20AC"));

            CellStyle styleTexteGras = classeur.createCellStyle();
            styleTexteGras.setFont(policeTotal);
            styleTexteGras.setAlignment(HorizontalAlignment.LEFT);

            construireDetail(classeur, lignes, styleEntete, styleDate, styleMontant);
            construireSynthese(classeur, lignes, debut, fin,
                styleEntete, styleMontant, styleTotal, styleTexteGras);

            classeur.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de generer le fichier Excel : " + e.getMessage());
        }
    }

    private void construireDetail(XSSFWorkbook classeur, List<LigneExportDto> lignes,
                                  CellStyle styleEntete, CellStyle styleDate,
                                  CellStyle styleMontant) {
        Sheet feuille = classeur.createSheet("Mouvements");
        int[] largeurs = {2800, 10000, 4200, 2600, 3600, 3000, 5200};
        for (int i = 0; i < largeurs.length; i++) {
            feuille.setColumnWidth(i, largeurs[i]);
        }

        Row entete = feuille.createRow(0);
        for (int i = 0; i < ENTETES.length; i++) {
            Cell cellule = entete.createCell(i);
            cellule.setCellValue(ENTETES[i]);
            cellule.setCellStyle(styleEntete);
        }

        int numero = 1;
        for (LigneExportDto ligne : lignes) {
            Row row = feuille.createRow(numero++);

            Cell cDate = row.createCell(0);
            cDate.setCellValue(ligne.getDate());
            cDate.setCellStyle(styleDate);

            row.createCell(1).setCellValue(ligne.getLibelle());
            row.createCell(2).setCellValue(libelleCategorie(ligne.getCategorie()));
            row.createCell(3).setCellValue(
                ligne.getType() == TypeOperation.DEBIT ? "Debit" : "Credit");

            Cell cMontant = row.createCell(4);
            cMontant.setCellValue(ligne.getMontantSigne().doubleValue());
            cMontant.setCellStyle(styleMontant);

            row.createCell(5).setCellValue(ligne.getOrigine());
            row.createCell(6).setCellValue(ligne.getStatut());
        }

        feuille.createFreezePane(0, 1);
        if (!lignes.isEmpty()) {
            feuille.setAutoFilter(new CellRangeAddress(0, lignes.size(), 0, ENTETES.length - 1));
        }
    }

    private void construireSynthese(XSSFWorkbook classeur, List<LigneExportDto> lignes,
                                    LocalDate debut, LocalDate fin, CellStyle styleEntete,
                                    CellStyle styleMontant, CellStyle styleTotal,
                                    CellStyle styleTexteGras) {
        Sheet feuille = classeur.createSheet("Synthese");
        feuille.setColumnWidth(0, 5200);
        feuille.setColumnWidth(1, 3800);
        feuille.setColumnWidth(2, 3800);
        feuille.setColumnWidth(3, 3800);

        Row titre = feuille.createRow(0);
        Cell celluleTitre = titre.createCell(0);
        celluleTitre.setCellValue("Periode du " + debut.format(DATE_FR) + " au " + fin.format(DATE_FR));
        celluleTitre.setCellStyle(styleTexteGras);

        Row entete = feuille.createRow(2);
        String[] colonnes = {"Categorie", "Debits", "Credits", "Solde"};
        for (int i = 0; i < colonnes.length; i++) {
            Cell cellule = entete.createCell(i);
            cellule.setCellValue(colonnes[i]);
            cellule.setCellStyle(styleEntete);
        }

        // On garde l ordre de l enum pour une lecture stable d un export a l autre
        Map<Categorie, BigDecimal[]> parCategorie = new LinkedHashMap<>();
        for (Categorie categorie : Categorie.values()) {
            parCategorie.put(categorie, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (LigneExportDto ligne : lignes) {
            BigDecimal[] totaux = parCategorie.get(ligne.getCategorie());
            if (ligne.getType() == TypeOperation.DEBIT) {
                totaux[0] = totaux[0].add(ligne.getMontant());
            } else {
                totaux[1] = totaux[1].add(ligne.getMontant());
            }
        }

        int numero = 3;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (Map.Entry<Categorie, BigDecimal[]> entree : parCategorie.entrySet()) {
            BigDecimal debits = entree.getValue()[0];
            BigDecimal credits = entree.getValue()[1];
            if (debits.signum() == 0 && credits.signum() == 0) continue;

            totalDebits = totalDebits.add(debits);
            totalCredits = totalCredits.add(credits);

            Row row = feuille.createRow(numero++);
            row.createCell(0).setCellValue(libelleCategorie(entree.getKey()));
            ecrireMontant(row, 1, debits, styleMontant);
            ecrireMontant(row, 2, credits, styleMontant);
            ecrireMontant(row, 3, credits.subtract(debits), styleMontant);
        }

        Row ligneTotal = feuille.createRow(numero + 1);
        Cell libelleTotal = ligneTotal.createCell(0);
        libelleTotal.setCellValue("Total");
        libelleTotal.setCellStyle(styleTexteGras);
        ecrireMontant(ligneTotal, 1, totalDebits, styleTotal);
        ecrireMontant(ligneTotal, 2, totalCredits, styleTotal);
        ecrireMontant(ligneTotal, 3, totalCredits.subtract(totalDebits), styleTotal);
    }

    private void ecrireMontant(Row row, int colonne, BigDecimal montant, CellStyle style) {
        Cell cellule = row.createCell(colonne);
        cellule.setCellValue(montant.doubleValue());
        cellule.setCellStyle(style);
    }

    /** Meme rendu que le front : PREMIERE lettre en majuscule, reste en minuscules. */
    private String libelleCategorie(Categorie categorie) {
        if (categorie == null) return "";
        String nom = categorie.name().toLowerCase();
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1);
    }
}
