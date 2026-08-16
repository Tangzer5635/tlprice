package fr.tlprice.services;

import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.ExceptionEcheanceRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import fr.tlprice.repositories.RapprochementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cycle de vie des operations recurrentes.
 *
 * Existe surtout pour la suppression, qui n est pas une operation isolee :
 * deux tables pointent vers `operation_recurrente` par cle etrangere, et
 * PostgreSQL refuse de supprimer une ligne encore referencee.
 *
 * Le controleur appelait `repository.delete()` directement. Ca fonctionnait
 * tant qu une operation n avait ni ajustement ponctuel ni ligne de controle —
 * autrement dit tant qu on ne s en servait pas vraiment. Des la premiere
 * analyse de l ecran Controle, chaque operation acquiert des lignes de
 * rapprochement, et la suppression echouait en violation de contrainte.
 */
@Service
public class OperationService {

    private static final Logger log = LoggerFactory.getLogger(OperationService.class);

    private final OperationRecurrenteRepository operationRepository;
    private final ExceptionEcheanceRepository exceptionRepository;
    private final RapprochementRepository rapprochementRepository;

    public OperationService(OperationRecurrenteRepository operationRepository,
                            ExceptionEcheanceRepository exceptionRepository,
                            RapprochementRepository rapprochementRepository) {
        this.operationRepository = operationRepository;
        this.exceptionRepository = exceptionRepository;
        this.rapprochementRepository = rapprochementRepository;
    }

    /** Ce qui disparaitra avec l operation, pour prevenir avant d agir. */
    @Transactional(readOnly = true)
    public Map<String, Object> impactSuppression(Utilisateur utilisateur, Long id) {
        OperationRecurrente operation = obligatoire(utilisateur, id);
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("libelle", operation.getLibelle());
        impact.put("ajustements", exceptionRepository.countByOperation(operation));
        impact.put("controles", rapprochementRepository.countByOperation(operation));
        return impact;
    }

    /**
     * Supprime l operation et tout ce qui en depend, dans une seule
     * transaction : soit les trois suppressions passent, soit aucune.
     *
     * L ordre importe. Les enfants d abord, le parent ensuite — l inverse
     * declenche la violation de contrainte qu on cherche justement a eviter.
     *
     * Les ajustements ponctuels n ont aucun sens sans leur operation : ils ne
     * font que modifier une de ses occurrences. Les lignes de controle sont
     * des traces historiques, mais elles pointent vers l operation par une
     * colonne non nulle — les conserver demanderait de rendre ce lien
     * facultatif, ce qui compliquerait tout le rapprochement pour garder des
     * lignes qui ne designent plus rien.
     *
     * Pour garder l historique, la bonne action n est pas la suppression mais
     * la desactivation : l operation cesse de generer des echeances et tout
     * son passe reste consultable.
     */
    @Transactional
    public Map<String, Object> supprimer(Utilisateur utilisateur, Long id) {
        OperationRecurrente operation = obligatoire(utilisateur, id);

        long ajustements = exceptionRepository.countByOperation(operation);
        long controles = rapprochementRepository.countByOperation(operation);

        exceptionRepository.deleteByOperation(operation);
        rapprochementRepository.deleteByOperation(operation);
        operationRepository.delete(operation);

        log.info("Operation {} supprimee ({} ajustement(s), {} ligne(s) de controle)",
            operation.getLibelle(), ajustements, controles);

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("supprime", true);
        resultat.put("ajustements", ajustements);
        resultat.put("controles", controles);
        return resultat;
    }

    private OperationRecurrente obligatoire(Utilisateur utilisateur, Long id) {
        return operationRepository.findByIdAndUtilisateur(id, utilisateur)
            .orElseThrow(() -> new IllegalStateException("Operation introuvable"));
    }
}
