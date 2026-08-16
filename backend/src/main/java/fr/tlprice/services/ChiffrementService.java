package fr.tlprice.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement AES-256-GCM des cles privees Enable Banking.
 * La cle maitre vient de TLPRICE_CLE_CHIFFREMENT ; si absente, une cle
 * est generee et persistee dans ./data/cle.secret (pratique en dev,
 * acceptable en prod si le dossier data est sur un volume sauvegarde).
 */
@Service
public class ChiffrementService {

    private static final Logger log = LoggerFactory.getLogger(ChiffrementService.class);
    private static final int TAILLE_IV = 12;
    private static final int TAILLE_TAG = 128;
    private static final Path FICHIER_CLE = Path.of("data", "cle.secret");

    private final SecretKeySpec cle;
    private final SecureRandom random = new SecureRandom();

    public ChiffrementService(@Value("${tlprice.cle-chiffrement:}") String cleBase64) {
        this.cle = new SecretKeySpec(resoudreCle(cleBase64), "AES");
    }

    private byte[] resoudreCle(String cleBase64) {
        try {
            if (cleBase64 != null && !cleBase64.isBlank()) {
                byte[] octets = Base64.getDecoder().decode(cleBase64.trim());
                if (octets.length != 32) {
                    throw new IllegalStateException(
                        "TLPRICE_CLE_CHIFFREMENT doit faire 32 octets en base64. "
                        + "Genere-la avec : openssl rand -base64 32");
                }
                return octets;
            }
            if (Files.exists(FICHIER_CLE)) {
                return Base64.getDecoder().decode(Files.readString(FICHIER_CLE).trim());
            }
            byte[] nouvelle = new byte[32];
            new SecureRandom().nextBytes(nouvelle);
            Files.createDirectories(FICHIER_CLE.getParent());
            Files.writeString(FICHIER_CLE, Base64.getEncoder().encodeToString(nouvelle));
            log.warn("Aucune cle de chiffrement fournie : cle generee dans {}. "
                + "Sauvegarde ce fichier, sans lui les configs bancaires sont irrecuperables.",
                FICHIER_CLE.toAbsolutePath());
            return nouvelle;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Initialisation de la cle de chiffrement impossible", e);
        }
    }

    /**
     * Un secret derive de la cle maitre, pour un usage donne.
     *
     * Signer un cookie avec la cle AES elle-meme serait une mauvaise idee :
     * deux usages differents doivent avoir deux secrets differents, sinon la
     * fuite de l un compromet l autre. On derive donc par SHA-256 en salant
     * avec le nom de l usage — la cle de chiffrement ne sort jamais d ici.
     *
     * Le resultat est stable tant que TLPRICE_CLE_CHIFFREMENT ne change pas :
     * les cookies signes survivent aux redemarrages et aux redeploiements.
     */
    public String secretDerive(String usage) {
        try {
            MessageDigest empreinte = MessageDigest.getInstance("SHA-256");
            empreinte.update(cle.getEncoded());
            empreinte.update(usage.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(empreinte.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Derivation du secret impossible", e);
        }
    }

    /** Renvoie iv + ciphertext concatenes, encodes en base64. */
    public String chiffrer(String clair) {
        try {
            byte[] iv = new byte[TAILLE_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(TAILLE_TAG, iv));
            byte[] chiffre = cipher.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            byte[] resultat = new byte[iv.length + chiffre.length];
            System.arraycopy(iv, 0, resultat, 0, iv.length);
            System.arraycopy(chiffre, 0, resultat, iv.length, chiffre.length);
            return Base64.getEncoder().encodeToString(resultat);
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String dechiffrer(String chiffreBase64) {
        try {
            byte[] tout = Base64.getDecoder().decode(chiffreBase64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, cle,
                new GCMParameterSpec(TAILLE_TAG, tout, 0, TAILLE_IV));
            byte[] clair = cipher.doFinal(tout, TAILLE_IV, tout.length - TAILLE_IV);
            return new String(clair, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Dechiffrement impossible (cle de chiffrement changee ?)", e);
        }
    }
}
