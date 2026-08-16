import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { abonnementActuel } from "../push";

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
  /** Appele quand l appareil courant est deconnecte, pour rafraichir le bouton. */
  onAppareilCourantRetire?: () => void;
};

const hh = (h: number) => String(h).padStart(2, "0") + ":00";

const quand = (iso: string) =>
  new Date(iso).toLocaleDateString("fr-FR", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });

/**
 * Les appareils qui recoivent les notifications de ce compte.
 *
 * Chacun a sa propre heure — un rappel utile a midi sur le telephone tombe
 * au mauvais moment le soir sur l ordinateur. Et chacun peut etre deconnecte
 * ou renomme : un abonnement survit a la desinstallation de l app, si bien
 * qu on se retrouve avec trois lignes "iPhone" dont deux sont mortes.
 */
export default function BlocAppareils({ heureCompte, onAppareilCourantRetire }: Props) {
  const [appareils, setAppareils] = useState<Appareil[]>([]);
  const [endpointLocal, setEndpointLocal] = useState<string | null>(null);
  const [edition, setEdition] = useState<{ id: number; nom: string } | null>(null);
  const [confirmation, setConfirmation] = useState<number | null>(null);
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
    // Identifier l appareil courant permet de le marquer dans la liste.
    abonnementActuel()
      .then((sub) => setEndpointLocal(sub ? sub.endpoint : null))
      .catch(() => setEndpointLocal(null));
  }, [charger]);

  const reglerHeure = async (id: number, valeur: string) => {
    setErreur("");
    setMessage("");
    const heure = valeur === "compte" ? null : Number(valeur);
    try {
      await api.patch("/api/push/appareils/" + id + "/heure", { heure });
      setAppareils((l) => l.map((a) => (a.id === id ? { ...a, heureRecap: heure } : a)));
      setMessage(
        heure === null
          ? "Cet appareil suit de nouveau l heure du compte."
          : "Rappel regle sur " + hh(heure) + " pour cet appareil."
      );
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Enregistrement impossible");
    }
  };

  const renommer = async () => {
    if (!edition || !edition.nom.trim()) return;
    setErreur("");
    setMessage("");
    try {
      await api.patch("/api/push/appareils/" + edition.id + "/nom", { nom: edition.nom });
      setAppareils((l) =>
        l.map((a) => (a.id === edition.id ? { ...a, appareil: edition.nom.trim() } : a))
      );
      setEdition(null);
      setMessage("Appareil renomme.");
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Renommage impossible");
    }
  };

  const retirer = async (a: Appareil) => {
    setErreur("");
    setMessage("");
    const courant = endpointLocal !== null && a.endpoint === endpointLocal;
    try {
      // Si c est l appareil courant, on coupe aussi l abonnement cote
      // navigateur : sinon le bouton d activation continuerait d afficher
      // "Desactiver" pour un abonnement que le serveur ne connait plus.
      if (courant) {
        const sub = await abonnementActuel();
        if (sub) await sub.unsubscribe();
      }
      await api.supprimer("/api/push/appareils/" + a.id);
      setAppareils((l) => l.filter((x) => x.id !== a.id));
      setConfirmation(null);
      setMessage(
        courant
          ? "Cet appareil ne recevra plus de notifications."
          : a.appareil + " a ete deconnecte."
      );
      if (courant) {
        setEndpointLocal(null);
        if (onAppareilCourantRetire) onAppareilCourantRetire();
      }
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Deconnexion impossible");
    }
  };

  if (appareils.length === 0) return null;

  return (
    <div className="bloc-appareils">
      <h3 className="titre-sous-section">Appareils connectes</h3>
      <p className="note">
        Sans reglage, un appareil suit l heure du compte ({hh(heureCompte)}).
        Un abonnement survit a la desinstallation de l app : si tu vois des
        lignes en double, deconnecte les anciennes.
      </p>

      <ul className="liste-appareils">
        {appareils.map((a) => {
          const courant = endpointLocal !== null && a.endpoint === endpointLocal;
          const enEdition = edition !== null && edition.id === a.id;
          const aConfirmer = confirmation === a.id;
          return (
            <li key={a.id}>
              <span className="appareil-tete">
                {enEdition ? (
                  <input
                    className="input-nom"
                    value={edition.nom}
                    onChange={(e) => setEdition({ id: a.id, nom: e.target.value })}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") renommer();
                      if (e.key === "Escape") setEdition(null);
                    }}
                    autoFocus
                    maxLength={120}
                    aria-label="Nom de l appareil"
                  />
                ) : (
                  <span className="appareil-nom">
                    {a.appareil}
                    {courant && <em className="badge">cet appareil</em>}
                  </span>
                )}
                <em className="appareil-date">ajoute le {quand(a.dateCreation)}</em>
              </span>

              <span className="appareil-actions">
                <select
                  value={a.heureRecap === null ? "compte" : String(a.heureRecap)}
                  onChange={(e) => reglerHeure(a.id, e.target.value)}
                  aria-label={"Heure du rappel pour " + a.appareil}
                >
                  <option value="compte">Heure du compte ({hh(heureCompte)})</option>
                  {Array.from({ length: 24 }, (_, h) => (
                    <option key={h} value={h}>
                      {hh(h)}
                    </option>
                  ))}
                </select>

                {enEdition ? (
                  <>
                    <button className="bouton-texte" onClick={renommer}>
                      Enregistrer
                    </button>
                    <button className="bouton-texte" onClick={() => setEdition(null)}>
                      Annuler
                    </button>
                  </>
                ) : aConfirmer ? (
                  <>
                    {/* Confirmation en deux temps : la deconnexion est
                        irreversible, l appareil devra se reabonner. */}
                    <button className="bouton-texte danger" onClick={() => retirer(a)}>
                      Confirmer
                    </button>
                    <button className="bouton-texte" onClick={() => setConfirmation(null)}>
                      Annuler
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      className="bouton-texte"
                      onClick={() => setEdition({ id: a.id, nom: a.appareil })}
                    >
                      Renommer
                    </button>
                    <button
                      className="bouton-texte danger"
                      onClick={() => setConfirmation(a.id)}
                    >
                      Deconnecter
                    </button>
                  </>
                )}
              </span>
            </li>
          );
        })}
      </ul>

      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}
    </div>
  );
}
