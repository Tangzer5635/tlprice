package fr.tlprice.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import fr.tlprice.dto.BanqueDto;
import fr.tlprice.dto.TransactionBancaireDto;
import fr.tlprice.dto.StatutBanqueDto;
import fr.tlprice.entities.*;
import fr.tlprice.entities.OperationRecurrente;
import fr.tlprice.entities.Transaction;
import fr.tlprice.repositories.ConfigBancaireRepository;
import fr.tlprice.repositories.ConnexionBancaireRepository;
import fr.tlprice.repositories.EtatAutorisationRepository;
import fr.tlprice.repositories.OperationRecurrenteRepository;
import fr.tlprice.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration Enable Banking multi-utilisateurs : chaque utilisateur a sa
 * propre application EB (Application ID + cle privee), donc chaque appel
 * signe un JWT RS256 avec LES identifiants de CET utilisateur.
 */
@Service
public class EnableBankingService {

    private final ConfigBancaireRepository configRepository;
    private final ConnexionBancaireRepository connexionRepository;
    private final EtatAutorisationRepository etatRepository;
    private final TransactionRepository transactionRepository;
    private final OperationRecurrenteRepository operationRepository;
    private final ChiffrementService chiffrementService;
    private final SoldeService soldeService;
    private final RegleRenommageService reglesService;
    private final RestClient restClient;
    private final String redirectUrl;

    /** Cache des cles privees parsees, par id utilisateur. */
    private final Map<Long, PrivateKey> cacheCles = new ConcurrentHashMap<>();

    public EnableBankingService(ConfigBancaireRepository configRepository,
                                ConnexionBancaireRepository connexionRepository,
                                EtatAutorisationRepository etatRepository,
                                TransactionRepository transactionRepository,
                                OperationRecurrenteRepository operationRepository,
                                ChiffrementService chiffrementService,
                                SoldeService soldeService,
                                RegleRenommageService reglesService,
                                @Value("${tlprice.enablebanking.base-url}") String baseUrl,
                                @Value("${tlprice.redirect-url}") String redirectUrl) {
        this.configRepository = configRepository;
        this.connexionRepository = connexionRepository;
        this.etatRepository = etatRepository;
        this.transactionRepository = transactionRepository;
        this.operationRepository = operationRepository;
        this.chiffrementService = chiffrementService;
        this.soldeService = soldeService;
        this.reglesService = reglesService;
        this.redirectUrl = redirectUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ==================== Configuration ====================

    /** Valide et enregistre l Application ID + la cle privee de l utilisateur. */
    @Transactional
    public void enregistrerConfig(Utilisateur utilisateur, String applicationId, String pemClePrivee) {
        // Validation immediate : si le PEM ne se parse pas, on refuse tout de suite
        parserClePrivee(pemClePrivee);

        ConfigBancaire config = configRepository.findByUtilisateur(utilisateur)
            .orElseGet(() -> {
                ConfigBancaire c = new ConfigBancaire();
                c.setUtilisateur(utilisateur);
                return c;
            });
        config.setApplicationId(applicationId.trim());
        config.setClePriveeChiffree(chiffrementService.chiffrer(pemClePrivee));
        configRepository.save(config);
        cacheCles.remove(utilisateur.getId());
    }

    @Transactional
    public void choisirBanque(Utilisateur utilisateur, String nom, String pays) {
        ConfigBancaire config = configObligatoire(utilisateur);
        config.setNomBanque(nom);
        config.setPaysBanque(pays);
        configRepository.save(config);
    }

    @Transactional(readOnly = true)
    public StatutBanqueDto recupererStatut(Utilisateur utilisateur) {
        Optional<ConfigBancaire> config = configRepository.findByUtilisateur(utilisateur);
        Optional<ConnexionBancaire> connexion = connexionRepository.findByUtilisateur(utilisateur);
        boolean connectee = connexion.isPresent()
            && connexion.get().getValidUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        return new StatutBanqueDto(
            config.isPresent(),
            config.map(ConfigBancaire::getApplicationId).orElse(null),
            config.map(ConfigBancaire::getNomBanque).orElse(null),
            config.map(ConfigBancaire::getPaysBanque).orElse(null),
            connectee,
            connexion.map(ConnexionBancaire::getValidUntil).orElse(null));
    }

    // ==================== Liste des banques ====================

    @Transactional(readOnly = true)
    public List<BanqueDto> listerBanques(Utilisateur utilisateur, String pays) {
        ConfigBancaire config = configObligatoire(utilisateur);
        JsonNode reponse = restClient.get()
            .uri("/aspsps?country={pays}", pays)
            .header("Authorization", "Bearer " + genererJwt(utilisateur, config))
            .retrieve()
            .body(JsonNode.class);
        List<BanqueDto> banques = new ArrayList<>();
        if (reponse != null && reponse.get("aspsps") != null) {
            for (JsonNode b : reponse.get("aspsps")) {
                banques.add(new BanqueDto(
                    b.path("name").asText(),
                    b.path("country").asText(),
                    b.path("logo").asText(null)));
            }
        }
        banques.sort(Comparator.comparing(BanqueDto::getNom, String.CASE_INSENSITIVE_ORDER));
        return banques;
    }

    // ==================== Autorisation ====================

    /** Renvoie l URL de la page d authentification de la banque. */
    @Transactional
    public String demarrerAutorisation(Utilisateur utilisateur) {
        ConfigBancaire config = configObligatoire(utilisateur);
        if (config.getNomBanque() == null || config.getPaysBanque() == null) {
            throw new IllegalStateException("Choisis d abord ta banque dans la liste.");
        }

        // State stocke en base pour retrouver et verifier l utilisateur au retour
        etatRepository.deleteByDateCreationBefore(
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15));
        EtatAutorisation etat = new EtatAutorisation();
        etat.setState(UUID.randomUUID().toString());
        etat.setUtilisateur(utilisateur);
        etat.setDateCreation(OffsetDateTime.now(ZoneOffset.UTC));
        etatRepository.save(etat);

        // Verifie AVANT d envoyer l utilisateur chez sa banque : une fois
        // authentifie, il est trop tard, la banque le renverra la ou on lui a
        // dit — et si c est localhost, il atterrit sur une page morte sans
        // qu aucune trace n arrive au serveur.
        verifierRedirectUrl();

        Map<String, Object> banque = new HashMap<>();
        banque.put("name", config.getNomBanque());
        banque.put("country", config.getPaysBanque());

        Map<String, Object> acces = new HashMap<>();
        acces.put("valid_until", OffsetDateTime.now(ZoneOffset.UTC).plusDays(90).toString());

        Map<String, Object> corps = new HashMap<>();
        corps.put("access", acces);
        corps.put("aspsp", banque);
        corps.put("state", etat.getState());
        corps.put("redirect_url", verifierRedirectUrl());
        corps.put("psu_type", "personal");

        JsonNode reponse = restClient.post()
            .uri("/auth")
            .header("Authorization", "Bearer " + genererJwt(utilisateur, config))
            .body(corps)
            .retrieve()
            .body(JsonNode.class);

        if (reponse == null || reponse.get("url") == null) {
            throw new IllegalStateException("Reponse inattendue d Enable Banking : " + reponse);
        }
        return reponse.get("url").asText();
    }

    /**
     * Echange le code recu au retour de la banque contre une session.
     * Verifie le state et le consomme.
     */
    @Transactional
    public void finaliserConnexion(Utilisateur utilisateur, String code, String state) {
        EtatAutorisation etat = etatRepository.findById(state)
            .orElseThrow(() -> new IllegalStateException(
                "Autorisation inconnue ou expiree. Relance la connexion a ta banque."));
        if (!etat.getUtilisateur().getId().equals(utilisateur.getId())) {
            throw new IllegalStateException("Cette autorisation ne correspond pas a ton compte.");
        }
        etatRepository.delete(etat);

        ConfigBancaire config = configObligatoire(utilisateur);
        Map<String, Object> corps = new HashMap<>();
        corps.put("code", code);

        JsonNode reponse = restClient.post()
            .uri("/sessions")
            .header("Authorization", "Bearer " + genererJwt(utilisateur, config))
            .body(corps)
            .retrieve()
            .body(JsonNode.class);

        if (reponse == null || reponse.get("session_id") == null) {
            throw new IllegalStateException("Creation de session echouee : " + reponse);
        }

        JsonNode comptes = reponse.get("accounts");
        if (comptes == null || !comptes.isArray() || comptes.isEmpty()) {
            throw new IllegalStateException(
                "Aucun compte accessible. As-tu bien fait l etape "
                + "\"Activate by linking accounts\" dans TON Control Panel Enable Banking ? "
                + "En mode restreint, seuls les comptes lies a ton application sont visibles.");
        }

        String sessionId = reponse.get("session_id").asText();
        String accountUid = comptes.get(0).get("uid").asText();

        OffsetDateTime validUntil = OffsetDateTime.now(ZoneOffset.UTC).plusDays(90);
        JsonNode acces = reponse.get("access");
        if (acces != null && acces.get("valid_until") != null) {
            validUntil = OffsetDateTime.parse(acces.get("valid_until").asText());
        }

        ConnexionBancaire connexion = connexionRepository.findByUtilisateur(utilisateur)
            .orElseGet(() -> {
                ConnexionBancaire c = new ConnexionBancaire();
                c.setUtilisateur(utilisateur);
                return c;
            });
        connexion.setSessionId(sessionId);
        connexion.setAccountUid(accountUid);
        connexion.setValidUntil(validUntil);
        connexion.setDateConnexion(OffsetDateTime.now(ZoneOffset.UTC));
        connexionRepository.save(connexion);
    }

// ==================== Import : transactions bancaires ====================

    /**
     * Recupere l historique des transactions comptabilisees (BOOK) sur les
     * derniers jours. Sert a peupler l app sans tout resaisir a la main.
     */
    @Transactional(readOnly = true)
    public List<TransactionBancaireDto> listerTransactionsBancaires(Utilisateur utilisateur, int jours) {
        ConfigBancaire config = configObligatoire(utilisateur);
        ConnexionBancaire connexion = connexionRepository.findByUtilisateur(utilisateur)
            .orElseThrow(() -> new IllegalStateException(
                "Aucune connexion bancaire. Connecte d abord ta banque."));
        if (connexion.getValidUntil().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalStateException("Consentement expire. Reconnecte ta banque.");
        }

        java.time.LocalDate dateFrom = java.time.LocalDate.now().minusDays(Math.min(jours, 90));
        java.time.LocalDate dateTo = java.time.LocalDate.now();

        JsonNode reponse = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/accounts/{uid}/transactions")
                .queryParam("date_from", dateFrom.toString())
                .queryParam("date_to", dateTo.toString())
                .queryParam("transaction_status", "BOOK")
                .build(connexion.getAccountUid()))
            .header("Authorization", "Bearer " + genererJwt(utilisateur, config))
            .retrieve()
            .body(JsonNode.class);

        List<TransactionBancaireDto> transactions = new ArrayList<>();
        if (reponse != null && reponse.get("transactions") != null) {
            // Pre-charger transactions ponctuelles et operations recurrentes pour deduplication
            List<Transaction> ponctuelles = transactionRepository.findByUtilisateurOrderByDateOperationDesc(utilisateur);
            List<OperationRecurrente> operations = operationRepository.findByUtilisateurAndActiveTrue(utilisateur);
            var regles = reglesService.lister(utilisateur);

            for (JsonNode t : reponse.get("transactions")) {
                String libelle = extraireLibelle(t);
                String montantStr = t.path("transaction_amount").path("amount").asText(null);
                if (montantStr == null) continue;
                BigDecimal montant = new BigDecimal(montantStr).abs();
                String indicator = t.path("credit_debit_indicator").asText("");
                String type = "CRDT".equals(indicator) ? "CREDIT" : "DEBIT";
                String date = t.path("booking_date").asText(
                    t.path("value_date").asText(
                        t.path("transaction_date").asText("")));
                String reference = t.path("entry_reference").asText(
                    t.path("transaction_id").asText(null));

                // Application d une regle apprise
                String libelleAuto = null;
                fr.tlprice.entities.Categorie categorieAuto = null;
                for (var r : regles) {
                    if (NormalisationLibelle.matche(r.getMotif(), libelle)) {
                        libelleAuto = r.getLibelleFinal();
                        categorieAuto = r.getCategorie();
                        break;
                    }
                }

                // Detection deja importe : meme date + montant + type
                java.time.LocalDate dateJour = java.time.LocalDate.parse(date);
                fr.tlprice.entities.TypeOperation typeOp = "CREDIT".equals(type)
                    ? fr.tlprice.entities.TypeOperation.CREDIT
                    : fr.tlprice.entities.TypeOperation.DEBIT;
                boolean dejaImporte = ponctuelles.stream().anyMatch(p ->
                    p.getDateOperation().equals(dateJour)
                    && p.getType() == typeOp
                    && p.getMontant().compareTo(montant) == 0);
                // On regarde aussi les recurrentes qui tombent ce jour
                if (!dejaImporte) {
                    dejaImporte = operations.stream().anyMatch(o ->
                        o.getType() == typeOp
                        && o.getMontant().compareTo(montant) == 0
                        && o.getFrequence() == fr.tlprice.entities.Frequence.MENSUELLE
                        && o.getJourDuMois() != null
                        && o.getJourDuMois() == dateJour.getDayOfMonth());
                }

                transactions.add(new TransactionBancaireDto(
                    reference, date, libelle, libelleAuto, categorieAuto,
                    dejaImporte, montant, type));
            }
        }
        // Tri le plus recent en tete
        transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return transactions;
    }

    private String extraireLibelle(JsonNode t) {
        JsonNode remit = t.get("remittance_information");
        if (remit != null && remit.isArray() && remit.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode ligne : remit) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(ligne.asText(""));
            }
            String r = sb.toString().trim();
            if (!r.isEmpty()) return r;
        }
        String creditor = t.path("creditor").path("name").asText(null);
        if (creditor != null && !creditor.isBlank()) return creditor;
        String debtor = t.path("debtor").path("name").asText(null);
        if (debtor != null && !debtor.isBlank()) return debtor;
        return "Sans libelle";
    }

    // ==================== Synchronisation du solde ====================

    /**
     * Recupere le solde aupres de la banque et met a jour le Compte local.
     * Preference : disponible (ITAV) > attendu (XPCD) > comptabilise (CLBD).
     */
    @Transactional
    public Compte synchroniser(Utilisateur utilisateur) {
        ConfigBancaire config = configObligatoire(utilisateur);
        ConnexionBancaire connexion = connexionRepository.findByUtilisateur(utilisateur)
            .orElseThrow(() -> new IllegalStateException(
                "Aucune connexion bancaire. Connecte d abord ta banque."));
        if (connexion.getValidUntil().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalStateException(
                "Le consentement a expire. Reconnecte ta banque.");
        }

        JsonNode reponse = restClient.get()
            .uri("/accounts/{uid}/balances", connexion.getAccountUid())
            .header("Authorization", "Bearer " + genererJwt(utilisateur, config))
            .retrieve()
            .body(JsonNode.class);

        if (reponse == null || reponse.get("balances") == null) {
            throw new IllegalStateException("Reponse inattendue d Enable Banking : " + reponse);
        }

        BigDecimal solde = choisirSolde(reponse.get("balances"));
        if (solde == null) {
            throw new IllegalStateException("Aucun solde exploitable renvoye par la banque.");
        }
        return soldeService.mettreAJourSolde(utilisateur, solde);
    }

    private BigDecimal choisirSolde(JsonNode balances) {
        BigDecimal itav = null, xpcd = null, clbd = null, premier = null;
        for (JsonNode b : balances) {
            String type = b.path("balance_type").asText("");
            String montant = b.path("balance_amount").path("amount").asText(null);
            if (montant == null) continue;
            BigDecimal valeur = new BigDecimal(montant);
            if (premier == null) premier = valeur;
            switch (type) {
                case "ITAV" -> itav = valeur;
                case "XPCD" -> xpcd = valeur;
                case "CLBD" -> clbd = valeur;
            }
        }
        if (itav != null) return itav;
        if (xpcd != null) return xpcd;
        if (clbd != null) return clbd;
        return premier;
    }

    // ==================== JWT ====================

    private String genererJwt(Utilisateur utilisateur, ConfigBancaire config) {
        try {
            PrivateKey cle = cacheCles.computeIfAbsent(utilisateur.getId(),
                id -> parserClePrivee(chiffrementService.dechiffrer(config.getClePriveeChiffree())));
            Date maintenant = new Date();
            JWSHeader entete = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(config.getApplicationId())
                .build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("enablebanking.com")
                .audience("api.enablebanking.com")
                .issueTime(maintenant)
                .expirationTime(new Date(maintenant.getTime() + 3600_000L))
                .build();
            SignedJWT jwt = new SignedJWT(entete, claims);
            jwt.sign(new RSASSASigner(cle));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Signature du JWT Enable Banking impossible", e);
        }
    }

    private PrivateKey parserClePrivee(String pem) {
        try {
            String contenu = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                throw new IllegalStateException(
                    "Cle au format PKCS#1 non supporte. Convertis-la avec : "
                    + "openssl pkcs8 -topk8 -nocrypt -in cle.pem -out cle-pkcs8.pem");
            }
            byte[] octets = Base64.getDecoder().decode(contenu);
            return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(octets));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cle privee illisible. Verifie que c est bien le fichier .pem "
                + "telecharge lors de la creation de ton application Enable Banking.", e);
        }
    }

    private ConfigBancaire configObligatoire(Utilisateur utilisateur) {
        return configRepository.findByUtilisateur(utilisateur)
            .orElseThrow(() -> new IllegalStateException(
                "Configure d abord tes identifiants Enable Banking "
                + "(Application ID + cle privee) dans l onglet Banque."));
    }

    /**
     * L URL de renvoi doit etre celle du serveur qui tourne, pas celle du
     * poste de developpement.
     *
     * Jusqu a la v3, application.properties portait le defaut
     * "https://localhost:5173/banque/callback". Si TLPRICE_REDIRECT_URL
     * n etait pas transmise au conteneur — cas courant quand le compose de
     * production utilise "image:" et ne liste pas explicitement la variable —
     * ce defaut s appliquait sans un mot dans les logs. La banque renvoyait
     * alors vers localhost apres authentification : de son telephone,
     * l utilisateur tombait sur une page inaccessible, et le serveur ne voyait
     * jamais le code d autorisation.
     *
     * Le profil prod ne fournit plus de defaut. On echoue donc ici, avec un
     * message qui dit quoi corriger, plutot que dix minutes plus tard sans
     * explication.
     */
    public String verifierRedirectUrl() {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalStateException(
                "L URL de renvoi bancaire n est pas configuree. Ajoute "
                + "TLPRICE_REDIRECT_URL dans le docker-compose.yml du serveur "
                + "(ex : https://tondomaine.fr/banque/callback), puis relance "
                + "le conteneur.");
        }
        if (urlLocale(redirectUrl)) {
            throw new IllegalStateException(
                "L URL de renvoi bancaire pointe vers " + redirectUrl + ", "
                + "qui n est joignable que depuis le poste de developpement. "
                + "Ta banque y renverrait ton telephone apres authentification. "
                + "Corrige TLPRICE_REDIRECT_URL sur le serveur.");
        }
        return redirectUrl;
    }

    /** Vraie si l URL ne designe que la machine locale. */
    public static boolean urlLocale(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("//localhost") || u.contains("//127.0.0.1")
            || u.contains("//0.0.0.0") || u.contains("//[::1]");
    }

    /** Valeur brute, pour l ecran de diagnostic. */
    public String redirectUrlConfiguree() {
        return redirectUrl;
    }
}
