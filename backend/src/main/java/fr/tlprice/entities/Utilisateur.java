package fr.tlprice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "utilisateur", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** Ne doit jamais sortir dans une reponse JSON, meme par ricochet. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String motDePasseHash;

    @Column(nullable = false)
    private OffsetDateTime dateCreation = OffsetDateTime.now();
}
