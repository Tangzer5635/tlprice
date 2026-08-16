import { FormEvent, useState } from "react";
import { api, ErreurApi } from "../api";

export default function Connexion({ onConnecte }: { onConnecte: (email: string) => void }) {
  const [mode, setMode] = useState<"connexion" | "inscription">("connexion");
  const [email, setEmail] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [motDePasseVisible, setMotDePasseVisible] = useState(false);
  const [erreur, setErreur] = useState("");
  const [enCours, setEnCours] = useState(false);

  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    setErreur("");
    setEnCours(true);
    try {
      // Le cookie CSRF est publie par ce premier GET si absent
      await api.get("/api/auth/moi").catch(() => {});
      if (mode === "inscription") {
        await api.post("/api/auth/inscription", { email, motDePasse });
      }
      await api.postLogin(email, motDePasse);
      onConnecte(email.toLowerCase().trim());
    } catch (err) {
      if (err instanceof ErreurApi && err.statut === 401) {
        setErreur("Email ou mot de passe incorrect.");
      } else {
        setErreur(err instanceof Error ? err.message : "Erreur inattendue");
      }
    } finally {
      setEnCours(false);
    }
  };

  return (
    <div className="ecran-connexion">
      <div className="carte-connexion">
        <span className="marque grande">
          tlprice<span className="marque-point">·</span>
        </span>
        <p className="sous-titre">
          Ton solde projete, malgre le retard d affichage de ta banque.
        </p>
        <form onSubmit={soumettre} className="formulaire">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="username"
            />
          </label>
          <label>
            Mot de passe {mode === "inscription" && <em>(8 caracteres minimum)</em>}
            {/* Sur telephone, taper un mot de passe long en aveugle avec un
                clavier qui corrige tout seul est la premiere cause d echec de
                connexion. Le bouton reste hors du champ pour ne pas rogner la
                zone de frappe. */}
            <span className="champ-secret">
              <input
                type={motDePasseVisible ? "text" : "password"}
                value={motDePasse}
                onChange={(e) => setMotDePasse(e.target.value)}
                required
                minLength={mode === "inscription" ? 8 : undefined}
                autoComplete={mode === "inscription" ? "new-password" : "current-password"}
              />
              <button
                type="button"
                className="bascule-secret"
                onClick={() => setMotDePasseVisible(!motDePasseVisible)}
                aria-label={motDePasseVisible ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                aria-pressed={motDePasseVisible}
              >
                {motDePasseVisible ? "Masquer" : "Afficher"}
              </button>
            </span>
          </label>
          {erreur && <p className="message-erreur">{erreur}</p>}
          <button className="bouton" disabled={enCours}>
            {mode === "connexion" ? "Se connecter" : "Creer mon compte"}
          </button>
        </form>
        <button
          className="bouton-texte"
          onClick={() => {
            setMode(mode === "connexion" ? "inscription" : "connexion");
            setErreur("");
          }}
        >
          {mode === "connexion"
            ? "Premiere fois ici ? Creer un compte"
            : "Deja un compte ? Se connecter"}
        </button>
      </div>
    </div>
  );
}
