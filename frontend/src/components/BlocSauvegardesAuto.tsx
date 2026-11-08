import { useCallback, useEffect, useState } from "react";
import { api, telecharger, FichierSauvegarde } from "../api";

const poids = (octets: number) => {
  if (octets < 1024) return octets + " o";
  if (octets < 1024 * 1024) return Math.round(octets / 1024) + " Ko";
  return (octets / (1024 * 1024)).toFixed(1) + " Mo";
};

const quand = (iso: string) =>
  new Date(iso).toLocaleDateString("fr-FR", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });

/**
 * Les sauvegardes ecrites chaque nuit par le serveur.
 *
 * Elles existaient deja cote backend depuis la v3, mais une sauvegarde qu on
 * ne voit pas est une sauvegarde dont on ne sait pas si elle fonctionne — et
 * on ne le decouvre que le jour ou on en a besoin, c est-a-dire trop tard.
 */
export default function BlocSauvegardesAuto() {
  const [fichiers, setFichiers] = useState<FichierSauvegarde[]>([]);
  const [occupe, setOccupe] = useState(false);
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    try {
      setFichiers(await api.get("/api/donnees/sauvegardes"));
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Lecture impossible");
    }
  }, []);

  useEffect(() => {
    charger();
  }, [charger]);

  const sauvegarderMaintenant = async () => {
    setOccupe(true);
    setErreur("");
    setMessage("");
    try {
      const r = await api.post("/api/donnees/sauvegardes");
      setMessage("Sauvegarde ecrite : " + r.nom);
      await charger();
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Sauvegarde impossible");
    } finally {
      setOccupe(false);
    }
  };

  const recuperer = async (nom: string) => {
    setErreur("");
    try {
      await telecharger(
        "/api/donnees/sauvegardes/telecharger?nom=" + encodeURIComponent(nom), nom);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Telechargement impossible");
    }
  };

  return (
    <section className="carte-parametre">
      <h2 className="titre-section">Sauvegardes automatiques</h2>

      <div className="export-actions">
        <button
          type="button"
          className="bouton secondaire"
          disabled={occupe}
          onClick={sauvegarderMaintenant}
        >
          {occupe ? "Ecriture…" : "Sauvegarder maintenant"}
        </button>
      </div>

      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}

      {fichiers.length === 0 ? (
        <p className="vide">Aucune sauvegarde.</p>
      ) : (
        <ul className="liste-sauvegardes">
          {fichiers.map((f) => (
            <li key={f.nom}>
              <span className="sauvegarde-quand">{quand(f.date)}</span>
              <span className="sauvegarde-poids">{poids(f.taille)}</span>
              <button className="bouton-texte" onClick={() => recuperer(f.nom)}>
                Recuperer
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
