import { useRef, useState } from "react";
import { api, telecharger } from "../api";

type Rapport = {
  mode: string;
  resume: string;
};

/**
 * Transfert d un compte entre deux instances de tlprice : on telecharge un
 * JSON depuis l instance source (le poste local, par exemple) et on le
 * depose sur l instance cible (le Raspberry Pi).
 */
export default function BlocSauvegarde() {
  const [mode, setMode] = useState<"fusion" | "remplacement">("fusion");
  const [avecSolde, setAvecSolde] = useState(false);
  const [avecParametres, setAvecParametres] = useState(false);
  const [occupe, setOccupe] = useState(false);
  const [rapport, setRapport] = useState<Rapport | null>(null);
  const [erreur, setErreur] = useState("");
  const champFichier = useRef<HTMLInputElement>(null);

  const exporter = async () => {
    setErreur("");
    setRapport(null);
    setOccupe(true);
    try {
      await telecharger("/api/donnees/export", "tlprice-sauvegarde.json");
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Export impossible");
    } finally {
      setOccupe(false);
    }
  };

  const choisirFichier = () => champFichier.current?.click();

  const importer = async (fichier: File) => {
    setErreur("");
    setRapport(null);

    if (mode === "remplacement") {
      const ok = window.confirm(
        "Le mode remplacement efface d abord TOUTES les operations recurrentes, " +
          "transactions et regles de ce compte, puis charge le fichier. Continuer ?"
      );
      if (!ok) return;
    }

    setOccupe(true);
    try {
      const texte = await fichier.text();
      let contenu: unknown;
      try {
        contenu = JSON.parse(texte);
      } catch {
        throw new Error("Ce fichier n est pas un JSON valide.");
      }
      const url =
        "/api/donnees/import?mode=" + mode +
        "&solde=" + avecSolde +
        "&parametres=" + avecParametres;
      const reponse = await api.post(url, contenu);
      setRapport(reponse);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Import impossible");
    } finally {
      setOccupe(false);
      if (champFichier.current) champFichier.current.value = "";
    }
  };

  return (
    <section className="carte-parametre">
      <h2 className="titre-section">Sauvegarde et transfert</h2>

      <div className="export-actions">
        <button
          type="button"
          className="bouton"
          disabled={occupe}
          onClick={exporter}
        >
          Telecharger la sauvegarde
        </button>
      </div>

      <hr className="separateur-bloc" />

      <p className="hero-eyebrow">Restaurer un fichier</p>

      <div className="ligne-champ">
        <span className="etiquette">Mode</span>
        <select
          value={mode}
          onChange={(e) => setMode(e.target.value as "fusion" | "remplacement")}
        >
          <option value="fusion">Fusion (ajoute ce qui manque)</option>
          <option value="remplacement">Remplacement (efface puis charge)</option>
        </select>
      </div>

      <label className="ligne-interrupteur">
        <input
          type="checkbox"
          checked={avecSolde}
          onChange={(e) => setAvecSolde(e.target.checked)}
        />
        <span>Reprendre aussi le solde bancaire du fichier</span>
      </label>
      <label className="ligne-interrupteur">
        <input
          type="checkbox"
          checked={avecParametres}
          onChange={(e) => setAvecParametres(e.target.checked)}
        />
        <span>Reprendre aussi les preferences de notification</span>
      </label>

      <div className="export-actions">
        <button
          type="button"
          className="bouton secondaire"
          disabled={occupe}
          onClick={choisirFichier}
        >
          {occupe ? "Traitement..." : "Choisir un fichier JSON"}
        </button>
        <input
          ref={champFichier}
          type="file"
          accept="application/json,.json"
          style={{ display: "none" }}
          onChange={(e) => {
            const fichier = e.target.files?.[0];
            if (fichier) importer(fichier);
          }}
        />
      </div>

      {rapport && <p className="message-info">{rapport.resume}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}
    </section>
  );
}
