package fr.tlprice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.tlprice.dto.AbonnementPushDto;
import fr.tlprice.entities.AbonnementPush;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.AbonnementPushRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web Push (norme RFC 8030 / VAPID). Fonctionne sur iPhone depuis iOS 16.4,
 * a une condition : l app doit avoir ete ajoutee a l ecran d accueil. Dans
 * un onglet Safari, Apple ne delivre rien.
 *
 * Les cles VAPID sont propres a l instance et doivent rester stables : si
 * elles changent, tous les abonnements existants deviennent invalides et
 * chaque appareil doit se reabonner.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final AbonnementPushRepository abonnementRepository;
    private final ObjectMapper json = new ObjectMapper();

    private final String clePublique;
    private final String clePrivee;
    private final String sujet;

    private nl.martijndwars.webpush.PushService moteur;

    public PushService(AbonnementPushRepository abonnementRepository,
                       @Value("${tlprice.vapid.cle-publique:}") String clePublique,
                       @Value("${tlprice.vapid.cle-privee:}") String clePrivee,
                       @Value("${tlprice.vapid.sujet:mailto:contact@tlprice.local}") String sujet) {
        this.abonnementRepository = abonnementRepository;
        this.clePublique = clePublique;
        this.clePrivee = clePrivee;
        this.sujet = sujet;
    }

    @PostConstruct
    void initialiser() {
        if (!disponible()) {
            log.info("Push desactive : cles VAPID absentes "
                + "(TLPRICE_VAPID_CLE_PUBLIQUE / TLPRICE_VAPID_CLE_PRIVEE).");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            moteur = new nl.martijndwars.webpush.PushService(clePublique, clePrivee, sujet);
            log.info("Push actif.");
        } catch (Exception e) {
            moteur = null;
            log.error("Cles VAPID invalides, push desactive : {}", e.getMessage());
        }
    }

    /** Vrai si l instance sait envoyer des notifications. */
    public boolean disponible() {
        return clePublique != null && !clePublique.isBlank()
            && clePrivee != null && !clePrivee.isBlank();
    }

    public String clePublique() {
        return clePublique;
    }

    // =========================================================================
    // Abonnements
    // =========================================================================

    /** Idempotent : un meme endpoint ne cree jamais de doublon. */
    @Transactional
    public AbonnementPush enregistrer(Utilisateur utilisateur, AbonnementPushDto dto) {
        Optional<AbonnementPush> existant = abonnementRepository.findByEndpoint(dto.getEndpoint());
        AbonnementPush abonnement = existant.orElseGet(AbonnementPush::new);
        abonnement.setUtilisateur(utilisateur);
        abonnement.setEndpoint(dto.getEndpoint());
        abonnement.setCleP256dh(dto.getCleP256dh());
        abonnement.setCleAuth(dto.getCleAuth());
        if (dto.getAppareil() != null && !dto.getAppareil().isBlank()) {
            abonnement.setAppareil(dto.getAppareil().trim());
        }
        return abonnementRepository.save(abonnement);
    }

    @Transactional
    public void supprimer(Utilisateur utilisateur, String endpoint) {
        Optional<AbonnementPush> trouve = abonnementRepository.findByEndpoint(endpoint);
        if (trouve.isEmpty()) return;
        AbonnementPush abonnement = trouve.get();
        if (!abonnement.getUtilisateur().getId().equals(utilisateur.getId())) return;
        abonnementRepository.delete(abonnement);
    }

    @Transactional(readOnly = true)
    public List<AbonnementPush> appareils(Utilisateur utilisateur) {
        return abonnementRepository.findByUtilisateur(utilisateur);
    }

    @Transactional(readOnly = true)
    public boolean abonne(Utilisateur utilisateur) {
        return abonnementRepository.countByUtilisateur(utilisateur) > 0;
    }

    // =========================================================================
    // Envoi
    // =========================================================================

    /**
     * Envoie a tous les appareils de l utilisateur. Renvoie le nombre
     * d envois acceptes. Les abonnements morts (404 / 410) sont purges :
     * c est ce que renvoie Apple quand l app a ete desinstallee.
     */
    @Transactional
    public int envoyer(Utilisateur utilisateur, String titre, String corps, String url) {
        if (moteur == null) return 0;
        return envoyerA(abonnementRepository.findByUtilisateur(utilisateur), titre, corps, url);
    }

    /**
     * Envoie a une liste d appareils choisie par l appelant.
     *
     * Depuis la v4 chaque appareil a sa propre heure de recap : le planificateur
     * ne peut plus dire "tous les appareils de cet utilisateur", il doit viser
     * ceux dont l heure correspond a maintenant.
     */
    @Transactional
    public int envoyerA(List<AbonnementPush> abonnements, String titre, String corps, String url) {
        if (moteur == null) return 0;
        if (abonnements == null || abonnements.isEmpty()) return 0;

        Map<String, String> charge = new LinkedHashMap<>();
        charge.put("titre", titre);
        charge.put("corps", corps);
        charge.put("url", url == null ? "/" : url);
        byte[] contenu;
        try {
            contenu = json.writeValueAsBytes(charge);
        } catch (Exception e) {
            log.error("Serialisation de la notification impossible", e);
            return 0;
        }

        int envoyes = 0;
        for (AbonnementPush a : abonnements) {
            try {
                Notification notification = new Notification(
                    a.getEndpoint(), a.getCleP256dh(), a.getCleAuth(), contenu);
                HttpResponse reponse = moteur.send(notification);
                int statut = reponse.getStatusLine().getStatusCode();
                if (statut == 404 || statut == 410) {
                    log.info("Abonnement push expire, suppression : {}", court(a.getEndpoint()));
                    abonnementRepository.delete(a);
                } else if (statut >= 200 && statut < 300) {
                    envoyes++;
                } else {
                    log.warn("Push refuse ({}) pour {}", statut, court(a.getEndpoint()));
                }
            } catch (Exception e) {
                log.warn("Echec push vers {} : {}", court(a.getEndpoint()), e.getMessage());
            }
        }
        return envoyes;
    }

    /**
     * Retire un appareil du compte, par son identifiant.
     *
     * Distinct de supprimer(endpoint) : on veut pouvoir deconnecter un
     * appareil qu on n a pas sous la main — un vieux telephone, un abonnement
     * fantome laisse par une reinstallation. Cote navigateur, l abonnement
     * survit jusqu au prochain envoi, ou le service de push repondra 410 ;
     * l appareil cessera simplement de recevoir.
     */
    @Transactional
    public void supprimerParId(Utilisateur utilisateur, Long id) {
        AbonnementPush a = obligatoire(utilisateur, id);
        abonnementRepository.delete(a);
        log.info("Appareil {} retire du compte {}", id, utilisateur.getEmail());
    }

    /** Renomme un appareil, pour distinguer deux lignes identiques. */
    @Transactional
    public void renommer(Utilisateur utilisateur, Long id, String nom) {
        AbonnementPush a = obligatoire(utilisateur, id);
        String propre = nom == null ? "" : nom.trim();
        if (propre.isBlank()) {
            throw new IllegalStateException("Le nom ne peut pas etre vide.");
        }
        a.setAppareil(propre.length() <= 120 ? propre : propre.substring(0, 120));
        abonnementRepository.save(a);
    }

    /**
     * Charge un abonnement en verifiant qu il appartient au demandeur :
     * l identifiant vient du client, il ne fait pas foi.
     */
    private AbonnementPush obligatoire(Utilisateur utilisateur, Long id) {
        return abonnementRepository.findById(id)
            .filter(x -> x.getUtilisateur().getId().equals(utilisateur.getId()))
            .orElseThrow(() -> new IllegalStateException("Appareil introuvable."));
    }

    /**
     * Regle l heure de recap d un appareil. On verifie qu il appartient bien
     * au demandeur : l identifiant vient du client, il ne fait pas foi.
     */
    @Transactional
    public void reglerHeure(Utilisateur utilisateur, Long id, Integer heure) {
        AbonnementPush a = obligatoire(utilisateur, id);
        a.setHeureRecap(heure);
        abonnementRepository.save(a);
        log.info("Heure de recap de l appareil {} : {}", id,
            heure == null ? "suit le compte" : heure + "h");
    }

    private static String court(String endpoint) {
        if (endpoint == null) return "?";
        return endpoint.length() <= 48 ? endpoint : endpoint.substring(0, 48) + "…";
    }
}
