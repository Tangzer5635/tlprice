package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regle apprise a l import : quand un libelle bancaire matche le motif,
 * on remplace automatiquement par libelleFinal + categorie.
 */
@Entity
@Table(name = "regle_renommage")
@Getter
@Setter
@NoArgsConstructor
public class RegleRenommage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    // Jamais serialise : l utilisateur porte le hash du mot de passe.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Utilisateur utilisateur;

    /** Tokens caracteristiques du libelle bancaire d origine, joints par espace. */
    @Column(nullable = false, length = 500)
    private String motif;

    /** Le libelle que l utilisateur veut voir. */
    @Column(nullable = false)
    private String libelleFinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;
}
