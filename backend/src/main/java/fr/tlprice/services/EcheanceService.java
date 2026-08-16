package fr.tlprice.services;

import fr.tlprice.dto.MouvementDto;
import fr.tlprice.entities.ExceptionEcheance;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Genere les echeances des operations recurrentes sur une periode. */
@Service
public class EcheanceService {

    private final OperationRecurrenteRepository operationRepository;
    private final ExceptionEcheanceRepository exceptionRepository;

    public EcheanceService(OperationRecurrenteRepository operationRepository,
                           ExceptionEcheanceRepository exceptionRepository) {
        this.operationRepository = operationRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Transactional(readOnly = true)
    public List<MouvementDto> genererEcheances(Utilisateur utilisateur, LocalDate debut, LocalDate fin) {
        List<MouvementDto> echeances = new ArrayList<>();
        if (debut.isAfter(fin)) return echeances;

        List<OperationRecurrente> operations = operationRepository.findByUtilisateurAndActiveTrue(utilisateur);
        // Precharger les exceptions de toutes ces operations, indexees par (opId, date)
        Map<String, ExceptionEcheance> exceptions = new HashMap<>();
        if (!operations.isEmpty()) {
            for (ExceptionEcheance ex : exceptionRepository.findByOperationIn(operations)) {
                exceptions.put(ex.getOperation().getId() + "|" + ex.getDate(), ex);
            }
        }

        for (OperationRecurrente op : operations) {
            for (LocalDate date : datesEcheances(op, debut, fin)) {
                ExceptionEcheance ex = exceptions.get(op.getId() + "|" + date);
                if (ex != null && ex.isAnnulee()) continue;
                BigDecimal montant = op.getMontant();
                BigDecimal original = null;
                if (ex != null && ex.getMontantOverride() != null) {
                    original = op.getMontant();
                    montant = ex.getMontantOverride();
                }
                echeances.add(new MouvementDto(date, op.getLibelle(), montant,
                    op.getType(), op.getCategorie(), "ECHEANCE",
                    op.getId(), original));
            }
        }
        echeances.sort(Comparator.comparing(MouvementDto::getDate));
        return echeances;
    }

    private List<LocalDate> datesEcheances(OperationRecurrente op, LocalDate debut, LocalDate fin) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate borneDebut = max(debut, op.getDateDebut());
        LocalDate borneFin = op.getDateFin() != null && op.getDateFin().isBefore(fin)
            ? op.getDateFin() : fin;
        if (borneDebut.isAfter(borneFin)) return dates;

        switch (op.getFrequence()) {
            case MENSUELLE -> {
                int jour = op.getJourDuMois() != null ? op.getJourDuMois()
                    : op.getDateDebut().getDayOfMonth();
                LocalDate mois = borneDebut.withDayOfMonth(1);
                while (!mois.isAfter(borneFin)) {
                    LocalDate echeance = mois.withDayOfMonth(Math.min(jour, mois.lengthOfMonth()));
                    if (!echeance.isBefore(borneDebut) && !echeance.isAfter(borneFin)) {
                        dates.add(echeance);
                    }
                    mois = mois.plusMonths(1);
                }
            }
            case HEBDOMADAIRE -> {
                long ecart = java.time.temporal.ChronoUnit.DAYS
                    .between(op.getDateDebut(), borneDebut);
                long decalage = ecart % 7 == 0 ? 0 : 7 - (ecart % 7);
                LocalDate echeance = borneDebut.plusDays(decalage);
                while (!echeance.isAfter(borneFin)) {
                    dates.add(echeance);
                    echeance = echeance.plusDays(7);
                }
            }
            case ANNUELLE -> {
                for (int annee = borneDebut.getYear(); annee <= borneFin.getYear(); annee++) {
                    LocalDate echeance = ajusterAnniversaire(op.getDateDebut(), annee);
                    if (!echeance.isBefore(borneDebut) && !echeance.isAfter(borneFin)) {
                        dates.add(echeance);
                    }
                }
            }
        }
        return dates;
    }

    private LocalDate ajusterAnniversaire(LocalDate origine, int annee) {
        int jour = Math.min(origine.getDayOfMonth(),
            LocalDate.of(annee, origine.getMonth(), 1).lengthOfMonth());
        return LocalDate.of(annee, origine.getMonth(), jour);
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
