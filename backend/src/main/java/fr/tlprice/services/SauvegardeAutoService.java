package fr.tlprice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.tlprice.dto.FichierSauvegardeDto;
import fr.tlprice.dto.SauvegardeDto;
import fr.tlprice.entities.Utilisateur;
import fr.tlprice.repositories.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ecrit chaque nuit une copie du compte de chaque utilisateur sur le disque,
 * au meme format JSON que l export manuel.
 *
 * Pourquoi c est le point le plus important de cette version : depuis que
 * l historique du solde existe, la base accumule un point par jour que rien
 * ne peut reconstruire. La banque ne redonnera jamais le solde d un mardi de
 * l an dernier, et encore moins la projection que tlprice faisait ce jour-la.
 * Cette donnee vit sur une carte SD qui ecrit en continu, et les cartes SD
 * meurent — ce n est pas une hypothese, c est un calendrier.
 *
 * Deux precautions valent d etre notees. D abord l ecriture passe par un
 * fichier temporaire puis un deplacement atomique : une coupure de courant
 * pendant l ecriture laisse la sauvegarde precedente intacte plutot qu un
 * fichier tronque qui aurait l air valide. Ensuite la rotation garde les N
 * dernieres : sans elle, le repertoire finit par remplir la carte, ce qui
 * serait une facon particulierement ironique de perdre ses donnees.
 *
 * Cette sauvegarde reste locale a la machine. Elle protege d une corruption
 * de la base, pas de la mort du support : un rsync vers un autre disque
 * reste indispensable, et il est decrit dans le fichier A_LIRE.
 */
@Service
public class SauvegardeAutoService {

    private static final Logger log = LoggerFactory.getLogger(SauvegardeAutoService.class);

    private static final String PREFIXE = "tlprice-";
    private static final String EXTENSION = ".json";

    private final SauvegardeService sauvegardeService;
    private final UtilisateurRepository utilisateurRepository;
    private final ObjectMapper mapper;
    private final Path repertoire;
    private final int retention;

    public SauvegardeAutoService(
            SauvegardeService sauvegardeService,
            UtilisateurRepository utilisateurRepository,
            ObjectMapper mapper,
            @Value("${tlprice.sauvegarde.repertoire:./data/sauvegardes}") String repertoire,
            @Value("${tlprice.sauvegarde.retention:30}") int retention) {
        this.sauvegardeService = sauvegardeService;
        this.utilisateurRepository = utilisateurRepository;
        this.mapper = mapper;
        this.repertoire = Path.of(repertoire).toAbsolutePath().normalize();
        this.retention = Math.max(1, retention);
    }

    // =========================================================================
    // Declenchement automatique
    // =========================================================================

    /**
     * 3h15 : apres l historique du solde de 23h45, et loin des synchros
     * bancaires de la journee. La sauvegarde contient donc le point
     * d historique de la veille.
     */
    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Paris")
    public void sauvegardeNocturne() {
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        int reussies = 0;
        for (Utilisateur utilisateur : utilisateurs) {
            try {
                Path ecrit = ecrire(utilisateur);
                reussies++;
                log.info("Sauvegarde automatique ecrite : {}", ecrit.getFileName());
            } catch (Exception e) {
                // Un compte en echec ne doit pas empecher les autres : on
                // journalise et on continue.
                log.error("Sauvegarde automatique impossible pour {} : {}",
                    utilisateur.getEmail(), e.getMessage());
            }
        }
        log.info("Sauvegarde nocturne terminee : {}/{} compte(s)", reussies, utilisateurs.size());
    }

    // =========================================================================
    // Ecriture
    // =========================================================================

    /** Ecrit une sauvegarde datee du jour, puis purge les plus anciennes. */
    public Path ecrire(Utilisateur utilisateur) throws IOException {
        Files.createDirectories(repertoire);

        SauvegardeDto sauvegarde = sauvegardeService.exporter(utilisateur);
        Path cible = repertoire.resolve(nomFichier(utilisateur, LocalDate.now()));

        // Ecriture en deux temps : un fichier partiel ne remplace jamais une
        // sauvegarde valide.
        Path temporaire = Files.createTempFile(repertoire, "partiel-", EXTENSION);
        try {
            mapper.writeValue(temporaire.toFile(), sauvegarde);
            Files.move(temporaire, cible,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temporaire);
            throw e;
        }

        purger(utilisateur);
        purgerTemporaires();
        return cible;
    }

    /**
     * Une JVM tuee au mauvais moment laisse un "partiel-*.json" derriere elle.
     * Il n apparait dans aucune liste et ne sera jamais purge par la rotation,
     * qui ne connait que les fichiers correctement nommes : sans ce menage,
     * ces orphelins s accumulent silencieusement.
     */
    private void purgerTemporaires() {
        if (!Files.isDirectory(repertoire)) return;
        try (Stream<Path> contenu = Files.list(repertoire)) {
            for (Path p : contenu.filter(Files::isRegularFile).toList()) {
                if (!p.getFileName().toString().startsWith("partiel-")) continue;
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Temporaire non supprime : {}", p.getFileName());
                }
            }
        } catch (IOException e) {
            log.debug("Menage des temporaires impossible : {}", e.getMessage());
        }
    }

    /**
     * Ne garde que les {@code retention} sauvegardes les plus recentes de ce
     * compte. Les fichiers des autres comptes ne sont jamais touches.
     */
    private void purger(Utilisateur utilisateur) {
        List<Path> fichiers = fichiersDe(utilisateur);
        if (fichiers.size() <= retention) return;
        for (Path vieux : fichiers.subList(retention, fichiers.size())) {
            try {
                Files.delete(vieux);
                log.debug("Sauvegarde purgee : {}", vieux.getFileName());
            } catch (IOException e) {
                log.warn("Purge impossible pour {} : {}", vieux.getFileName(), e.getMessage());
            }
        }
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    /** Les sauvegardes de ce compte, de la plus recente a la plus ancienne. */
    public List<FichierSauvegardeDto> lister(Utilisateur utilisateur) {
        List<FichierSauvegardeDto> sortie = new ArrayList<>();
        for (Path fichier : fichiersDe(utilisateur)) {
            try {
                sortie.add(new FichierSauvegardeDto(
                    fichier.getFileName().toString(),
                    OffsetDateTime.ofInstant(
                        Files.getLastModifiedTime(fichier).toInstant(), ZoneId.systemDefault()),
                    Files.size(fichier)));
            } catch (IOException e) {
                log.warn("Fichier illisible ignore : {}", fichier.getFileName());
            }
        }
        return sortie;
    }

    /**
     * Resout un nom de fichier fourni par le client.
     *
     * Le nom vient de l exterieur : on refuse tout ce qui n appartient pas au
     * compte demandeur et tout ce qui pourrait sortir du repertoire, sinon
     * "../../etc/passwd" deviendrait un endpoint de lecture de fichiers.
     */
    public Path fichier(Utilisateur utilisateur, String nom) {
        if (nom == null || !nom.equals(Path.of(nom).getFileName().toString())) {
            throw new IllegalStateException("Nom de sauvegarde invalide.");
        }
        if (!nom.startsWith(prefixeDe(utilisateur)) || !nom.endsWith(EXTENSION)) {
            throw new IllegalStateException("Cette sauvegarde n appartient pas a ce compte.");
        }
        Path candidat = repertoire.resolve(nom).normalize();
        if (!candidat.startsWith(repertoire) || !Files.isRegularFile(candidat)) {
            throw new IllegalStateException("Sauvegarde introuvable.");
        }
        return candidat;
    }

    // =========================================================================
    // Nommage
    // =========================================================================

    private List<Path> fichiersDe(Utilisateur utilisateur) {
        if (!Files.isDirectory(repertoire)) return List.of();
        String prefixe = prefixeDe(utilisateur);
        try (Stream<Path> contenu = Files.list(repertoire)) {
            return contenu
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith(prefixe))
                .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String prefixeDe(Utilisateur utilisateur) {
        return PREFIXE + identifiant(utilisateur) + "-";
    }

    private String nomFichier(Utilisateur utilisateur, LocalDate date) {
        return prefixeDe(utilisateur) + date + EXTENSION;
    }

    /**
     * Un identifiant de fichier tire de l email. On ne garde que des
     * caracteres surs : l email sert de nom de fichier, et un "/" dans un
     * nom de fichier ne pardonne pas.
     */
    private String identifiant(Utilisateur utilisateur) {
        String base = utilisateur.getEmail() == null ? "" : utilisateur.getEmail();
        String propre = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (propre.isBlank()) propre = "compte-" + utilisateur.getId();
        return propre.length() <= 60 ? propre : propre.substring(0, 60);
    }
}
