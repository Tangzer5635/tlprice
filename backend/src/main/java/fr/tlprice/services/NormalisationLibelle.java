package fr.tlprice.services;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Normalisation des libelles bancaires pour permettre le matching :
 * accents, casse, ponctuation et chiffres sont ecartes ; les mots
 * generiques du jargon bancaire ("carte", "prlv", "sepa"...) aussi.
 */
public final class NormalisationLibelle {

    private static final Set<String> MOTS_JARGON = Set.of(
        "cb", "carte", "vir", "prlv", "sepa", "achat", "paiement",
        "com", "eur", "euro", "fr", "france",
        "de", "du", "des", "la", "le", "les", "aux", "au", "en", "sur",
        "pour", "par", "avec", "sans", "et", "ou"
    );

    private NormalisationLibelle() {}

    /** Renvoie les tokens signifiants du libelle (mots >=3 char, hors jargon). */
    public static List<String> tokens(String libelle) {
        if (libelle == null || libelle.isBlank()) return List.of();
        String texte = Normalizer.normalize(libelle, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9 ]+", " ")
            .replaceAll("\\d+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return Arrays.stream(texte.split(" "))
            .filter(t -> t.length() >= 3)
            .filter(t -> !MOTS_JARGON.contains(t))
            .distinct()
            .toList();
    }

    /**
     * True si les tokens du motif suffisent a reconnaitre libelleBrut :
     * il faut qu au moins 60% des tokens du motif soient presents,
     * avec un plancher de 2 tokens communs (ou 1 si le motif n en a qu un).
     */
    public static boolean matche(String motif, String libelleBrut) {
        List<String> tokensMotif = Arrays.asList(motif.split(" "));
        List<String> tokensBrut = tokens(libelleBrut);
        if (tokensMotif.isEmpty()) return false;
        long communs = tokensMotif.stream().filter(tokensBrut::contains).count();
        int minRequis = tokensMotif.size() == 1 ? 1 : 2;
        return communs >= minRequis
            && communs >= Math.ceil(tokensMotif.size() * 0.6);
    }

    /** Motif stocke = tokens signifiants du libelle brut, joints par espace. */
    public static String motifDe(String libelleBrut) {
        return String.join(" ", tokens(libelleBrut));
    }
}
