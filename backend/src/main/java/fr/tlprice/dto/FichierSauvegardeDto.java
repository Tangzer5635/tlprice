package fr.tlprice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Une sauvegarde nocturne presente sur le disque du serveur.
 *
 * Le nom est l identifiant : c est lui qu on repasse a l endpoint de
 * telechargement. Il porte deja la date, mais on expose aussi la date de
 * derniere ecriture, qui est la seule fiable si l horloge du Pi a derive.
 */
@Getter
@AllArgsConstructor
public class FichierSauvegardeDto {

    private String nom;

    /** Date d ecriture du fichier sur le disque. */
    private OffsetDateTime date;

    /** Taille en octets, pour reperer une sauvegarde anormalement petite. */
    private long taille;
}
