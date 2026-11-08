package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Une hypothese : « et si j achete ca a 400 euros le 12 ? »
 *
 * Le geste qu on fait devant un achat qui hesite, c est de recalculer sa fin
 * de mois de tete. tlprice sait deja faire ce calcul, il lui manquait juste
 * la possibilite d y ajouter des lignes qui n existent pas encore.
 *
 * Un scenario n est jamais compte dans la projection reelle : il ne
 * s applique que si on le selectionne explicitement, et le solde sans
 * scenario reste affiche a cote pour que la comparaison soit immediate.
 *
 * Table neuve : aucun impact sur les donnees existantes.
 */
@Entity
@Table(name = "scenario")
@Getter
@Setter
@NoArgsConstructor
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 120)
    private String nom;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneScenario> lignes = new ArrayList<>();

    @Column(nullable = false)
    private OffsetDateTime dateCreation = OffsetDateTime.now();
}
