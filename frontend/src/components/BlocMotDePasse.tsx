import { FormEvent, useState } from "react";
import { api } from "../api";

/**
 * Changement de mot de passe.
 *
 * Le cookie de connexion persistant est signe avec le hash du mot de passe :
 * le changer deconnecte tous les autres appareils. C est voulu — c est la
 * seule facon de couper un appareil perdu ou un mot de passe qui a fuite.
 */
export default function BlocMotDePasse() {
  const [actuel, setActuel] = useState("");
  const [nouveau, setNouveau] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [message, setMessage] = useState<{ ok: boolean; texte: string } | null>(null);
  const [enCours, setEnCours] = useState(false);

  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    if (nouveau !== confirmation) {
      setMessage({ ok: false, texte: "Les deux nouveaux mots de passe ne correspondent pas." });
      return;
    }
    setEnCours(true);
    try {
      await api.post("/api/auth/mot-de-passe", { actuel, nouveau });
      setActuel("");
      setNouveau("");
      setConfirmation("");
      setMessage({
        ok: true,
        texte: "Mot de passe change. Tes autres appareils devront se reconnecter.",
      });
    } catch (err) {
      setMessage({ ok: false, texte: err instanceof Error ? err.message : "Echec" });
    } finally {
      setEnCours(false);
    }
  };

  return (
    <section className="carte-parametre">
      <p className="hero-eyebrow">Mot de passe</p>
      <form className="formulaire" onSubmit={soumettre}>
        {/* Champ cache : permet au trousseau iCloud d associer le nouveau
            mot de passe au bon compte. */}
        <input type="text" autoComplete="username" hidden readOnly />
        <label>
          Mot de passe actuel
          <input
            type="password"
            value={actuel}
            onChange={(e) => setActuel(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <label>
          Nouveau mot de passe <em>(8 caracteres minimum)</em>
          <input
            type="password"
            value={nouveau}
            onChange={(e) => setNouveau(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        <label>
          Confirmation
          <input
            type="password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        {message && (
          <p className={message.ok ? "message-info" : "message-erreur"}>{message.texte}</p>
        )}
        <button className="bouton" disabled={enCours}>
          Changer le mot de passe
        </button>
      </form>
    </section>
  );
}
