import { useCallback, useEffect, useState } from "react";
import { api } from "../api";

type Appareil = {
  id: number;
  appareil: string;
  endpoint: string;
  dateCreation: string;
  /** null = suit l heure globale du compte. */
  heureRecap: number | null;
};

type Props = {
  /** Heure globale du compte, servant de defaut aux appareils non regles. */
  heureCompte: number;
};

const hh = (h: number) => String(h).padStart(2, "0") + ":00";

/**
 * Une heure de notification par appareil.
 *
 * Le meme compte est ouvert sur un iPhone et sur un PC de bureau, mais un
 * rappel utile a midi sur le telephone arrive au mauvais moment le soir sur
 * l ordinateur. Avec une heure unique par compte, il fallait choisir lequel
 * des deux serait mal servi.
 *
 * Ne pas regler un appareil le laisse sur l heure du compte : c est l etat de
 * tous les appareils apres mise a jour, rien ne change tant qu on n y touche
 * pas.
 */
export default function BlocHeuresAppareils({ heureCompte }: Props) {
  const [appareils, setAppareils] = useState<Appareil[]>([]);
  const [endpointLocal, setEndpointLocal] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    try {
      setAppareils(await api.get("/api/push/appareils"));
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Lecture impossible");
    }
  }, []);

  useEffect(() => {
    charger();
    // Identifier l appareil courant permet de le marquer dans la liste :
    // sans ca, deux iPhone ou deux Chrome sont indiscernables.
    navigator.serviceWorker?.ready
      .then((r) => r.pushManager.getSubscription())
      .then((sub) => setEndpointLocal(sub ? sub.endpoint : null))
      .catch(() => setEndpointLocal(null));
  }, [charger]);

  const regler = async (id: number, valeur: string) => {
    setErreur("");
    setMessage("");
    const heure = valeur === "compte" ? null : Number(valeur);
    try {
      await api.patch("/api/push/appareils/" + id + "/heure", { heure });
      setAppareils((liste) =>
        liste.map((a) => (a.id === id ? { ...a, heureRecap: heure } : a))
      );
      setMessage(
        heure === null
          ? "Cet appareil suit de nouveau l heure du compte."
          : "Rappel regle sur " + hh(heure) + " pour cet appareil."
      );
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Enregistrement impossible");
    }
  };

  if (appareils.length === 0) return null;

  return (
    <div className="bloc-appareils">
      <h3 className="titre-sous-section">Heure par appareil</h3>
      <p className="note">
        Sans reglage, un appareil suit l heure du compte ({hh(heureCompte)}).
      </p>

      <ul className="liste-appareils">
        {appareils.map((a) => {
          const courant = endpointLocal !== null && a.endpoint === endpointLocal;
          return (
            <li key={a.id}>
              <span className="appareil-nom">
                {a.appareil}
                {courant && <em className="badge">cet appareil</em>}
              </span>
              <select
                value={a.heureRecap === null ? "compte" : String(a.heureRecap)}
                onChange={(e) => regler(a.id, e.target.value)}
                aria-label={"Heure du rappel pour " + a.appareil}
              >
                <option value="compte">Heure du compte ({hh(heureCompte)})</option>
                {Array.from({ length: 24 }, (_, h) => (
                  <option key={h} value={h}>
                    {hh(h)}
                  </option>
                ))}
              </select>
            </li>
          );
        })}
      </ul>

      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}
    </div>
  );
}
