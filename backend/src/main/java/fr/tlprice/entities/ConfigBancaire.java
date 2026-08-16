package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Les identifiants Enable Banking PROPRES a chaque utilisateur :
 * chacun cree sa propre application sur enablebanking.com et fournit
 * son Application ID + sa cle privee (stockee chiffree AES-GCM).
 */
@Entity
@Table(name = "config_bancaire")
@Getter
@Setter
@NoArgsConstructor
public class ConfigBancaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private String applicationId;

    /** Cle privee PEM chiffree (AES-GCM, base64). */
    @Lob
    @Column(nullable = false, length = 16384)
    private String clePriveeChiffree;

    /** Banque choisie par l utilisateur (nom exact attendu par l API). */
    private String nomBanque;

    private String paysBanque;
}
