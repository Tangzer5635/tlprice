package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Config SMTP propre a chaque utilisateur ; mot de passe stocke chiffre. */
@Entity
@Table(name = "config_smtp")
@Getter
@Setter
@NoArgsConstructor
public class ConfigSmtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 587;

    @Column(nullable = false)
    private String username;

    /** Mot de passe chiffre AES-GCM. */
    @Column(nullable = false, length = 1024)
    private String motDePasseChiffre;

    /** Adresse d expedition ; si null, utilise username. */
    private String expediteur;

    @Column(nullable = false)
    private boolean starttls = true;
}
