package fr.tlprice.services;

import fr.tlprice.dto.MouvementDto;
import fr.tlprice.entities.Categorie;
import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.Frequence;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.TypeOperation;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * La generation des echeances est le socle de l application : le solde
 * projete, le recap quotidien, le rapprochement bancaire et l historique en
 * dependent tous les quatre. Un bug sur le mensuel du 31 ne se contente pas
 * de fausser un ecran, il se propage dans la notification du matin, dans le
 * controle et dans la courbe en meme temps — d ou ces tests.
 */
@ExtendWith(MockitoExtension.class)
class EcheanceServiceTest {

    @Mock
    private OperationRecurrenteRepository operationRepository;

    @Mock
    private ExceptionEcheanceRepository exceptionRepository;

    private EcheanceService service;
    private Utilisateur utilisateur;

    @BeforeEach
    void preparer() {
        service = new EcheanceService(operationRepository, exceptionRepository);
        utilisateur = new Utilisateur();
        utilisateur.setId(1L);
        utilisateur.setEmail("test@tlprice.local");
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private OperationRecurrente operation(Frequence frequence, Integer jourDuMois,
                                          LocalDate debut, String montant) {
        OperationRecurrente op = new OperationRecurrente();
        op.setId(10L);
        op.setUtilisateur(utilisateur);
        op.setLibelle("Loyer");
        op.setMontant(new BigDecimal(montant));
        op.setType(TypeOperation.DEBIT);
        op.setFrequence(frequence);
        op.setJourDuMois(jourDuMois);
        op.setCategorie(Categorie.LOGEMENT);
        op.setDateDebut(debut);
        op.setActive(true);
        return op;
    }

    private void aveuglerLesOperations(OperationRecurrente... operations) {
        when(operationRepository.findByUtilisateurAndActiveTrue(utilisateur))
            .thenReturn(List.of(operations));
        when(exceptionRepository.findByOperationIn(anyList())).thenReturn(List.of());
    }

    private void aveuglerAvecException(OperationRecurrente op, ExceptionEcheance exception) {
        when(operationRepository.findByUtilisateurAndActiveTrue(utilisateur))
            .thenReturn(List.of(op));
        when(exceptionRepository.findByOperationIn(anyList())).thenReturn(List.of(exception));
    }

    private ExceptionEcheance exception(OperationRecurrente op, LocalDate date) {
        ExceptionEcheance ex = new ExceptionEcheance();
        ex.setOperation(op);
        ex.setDate(date);
        return ex;
    }

    private List<LocalDate> datesDe(List<MouvementDto> mouvements) {
        return mouvements.stream().map(MouvementDto::getDate).toList();
    }

    // ------------------------------------------------------------------
    // Le mensuel du 31 : le cas qui casse tout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un prelevement le 31 tombe le dernier jour des mois plus courts")
    void mensuelLe31SeRabatSurLeDernierJour() {
        OperationRecurrente op =
            operation(Frequence.MENSUELLE, 31, LocalDate.of(2025, 1, 1), "800.00");
        aveuglerLesOperations(op);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 30));

        // Fevrier 2025 n a que 28 jours, avril que 30.
        assertThat(datesDe(echeances)).containsExactly(
            LocalDate.of(2025, 1, 31),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2025, 3, 31),
            LocalDate.of(2025, 4, 30));
    }

    @Test
    @DisplayName("Le 29 fevrier d une operation annuelle devient le 28 hors annee bissextile")
    void annuelLe29FevrierSAjuste() {
        OperationRecurrente op =
            operation(Frequence.ANNUELLE, null, LocalDate.of(2024, 2, 29), "120.00");
        aveuglerLesOperations(op);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(datesDe(echeances)).containsExactly(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2026, 2, 28));
    }

    // ------------------------------------------------------------------
    // Les bornes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Rien n est genere avant la date de debut ni apres la date de fin")
    void lesBornesDeLOperationSontRespectees() {
        OperationRecurrente op =
            operation(Frequence.MENSUELLE, 15, LocalDate.of(2025, 3, 1), "50.00");
        op.setDateFin(LocalDate.of(2025, 5, 20));
        aveuglerLesOperations(op);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(datesDe(echeances)).containsExactly(
            LocalDate.of(2025, 3, 15),
            LocalDate.of(2025, 4, 15),
            LocalDate.of(2025, 5, 15));
    }

    @Test
    @DisplayName("Une periode a l envers ne genere rien plutot que de boucler")
    void periodeInverseeRenvoieUneListeVide() {
        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 1));

        assertThat(echeances).isEmpty();
    }

    @Test
    @DisplayName("L hebdomadaire reste cale sur le jour de la date de debut")
    void hebdomadaireGardeSonJourDeSemaine() {
        // 6 janvier 2025 est un lundi.
        OperationRecurrente op =
            operation(Frequence.HEBDOMADAIRE, null, LocalDate.of(2025, 1, 6), "25.00");
        aveuglerLesOperations(op);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 8), LocalDate.of(2025, 2, 3));

        assertThat(datesDe(echeances)).containsExactly(
            LocalDate.of(2025, 1, 13),
            LocalDate.of(2025, 1, 20),
            LocalDate.of(2025, 1, 27),
            LocalDate.of(2025, 2, 3));
    }

    // ------------------------------------------------------------------
    // Les exceptions d echeance
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Une echeance annulee disparait de la projection")
    void echeanceAnnuleeEstIgnoree() {
        OperationRecurrente op =
            operation(Frequence.MENSUELLE, 5, LocalDate.of(2025, 1, 1), "60.00");
        ExceptionEcheance ex = exception(op, LocalDate.of(2025, 2, 5));
        ex.setAnnulee(true);
        aveuglerAvecException(op, ex);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31));

        assertThat(datesDe(echeances)).containsExactly(
            LocalDate.of(2025, 1, 5),
            LocalDate.of(2025, 3, 5));
    }

    @Test
    @DisplayName("Un montant ajuste remplace le montant par defaut et conserve l original")
    void montantOverrideRemplaceLeMontantEtGardeLaTrace() {
        OperationRecurrente op =
            operation(Frequence.MENSUELLE, 5, LocalDate.of(2025, 1, 1), "60.00");
        ExceptionEcheance ex = exception(op, LocalDate.of(2025, 2, 5));
        ex.setMontantOverride(new BigDecimal("75.50"));
        aveuglerAvecException(op, ex);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 3, 31));

        MouvementDto fevrier = echeances.get(0);
        assertThat(fevrier.getDate()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(fevrier.getMontant()).isEqualByComparingTo("75.50");
        assertThat(fevrier.getMontantOriginal()).isEqualByComparingTo("60.00");

        // Le mois suivant n est pas contamine par l ajustement ponctuel.
        MouvementDto mars = echeances.get(1);
        assertThat(mars.getMontant()).isEqualByComparingTo("60.00");
        assertThat(mars.getMontantOriginal()).isNull();
    }

    // ------------------------------------------------------------------
    // Assemblage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Les echeances de plusieurs operations sortent triees par date")
    void resultatTriieParDate() {
        OperationRecurrente loyer =
            operation(Frequence.MENSUELLE, 28, LocalDate.of(2025, 1, 1), "800.00");
        OperationRecurrente abonnement =
            operation(Frequence.MENSUELLE, 3, LocalDate.of(2025, 1, 1), "12.00");
        abonnement.setId(11L);
        abonnement.setLibelle("Streaming");
        aveuglerLesOperations(loyer, abonnement);

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 28));

        assertThat(datesDe(echeances)).isSorted();
        assertThat(echeances.get(0).getLibelle()).isEqualTo("Streaming");
    }

    @Test
    @DisplayName("Sans operation active, aucun appel inutile et aucune echeance")
    void aucuneOperationAucuneEcheance() {
        when(operationRepository.findByUtilisateurAndActiveTrue(utilisateur))
            .thenReturn(List.of());

        List<MouvementDto> echeances = service.genererEcheances(
            utilisateur, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(echeances).isEmpty();
    }
}
