import { useState } from "react";
import { formatLocal, telecharger } from "../api";

function debutDuMois(decalage = 0): string {
  const d = new Date();
  return formatLocal(new Date(d.getFullYear(), d.getMonth() + decalage, 1));
}

function finDuMois(decalage = 0): string {
  const d = new Date();
  return formatLocal(new Date(d.getFullYear(), d.getMonth() + decalage + 1, 0));
}

/**
 * Selection d une periode + telechargement CSV ou Excel.
 * Les echeances recurrentes sont incluses par defaut : sans elles
 * l export ne montre que les achats saisis a la main.
 */
export default function BlocExport() {
  const [debut, setDebut] = useState(debutDuMois());
  const [fin, setFin] = useState(finDuMois());
  const [echeances, setEcheances] = useState(true);
  const [enCours, setEnCours] = useState<"" | "csv" | "xlsx">("");
  const [erreur, setErreur] = useState("");

  const periode = (nbMois: number) => {
    setDebut(debutDuMois(-nbMois + 1));
    setFin(finDuMois());
  };

  const anneeEnCours = () => {
    const annee = new Date().getFullYear();
    setDebut(annee + "-01-01");
    setFin(annee + "-12-31");
  };

  const exporter = async (format: "csv" | "xlsx") => {
    setErreur("");
    setEnCours(format);
    try {
      const url =
        "/api/export?debut=" + debut +
        "&fin=" + fin +
        "&format=" + format +
        "&echeances=" + echeances;
      await telecharger(url, "tlprice-" + debut + "_" + fin + "." + format);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Export impossible");
    } finally {
      setEnCours("");
    }
  };

  return (
    <div className="carte-parametre bloc-export">
      <p className="hero-eyebrow">Exporter</p>

      <div className="export-raccourcis">
        <button type="button" className="bouton-texte" onClick={() => periode(1)}>
          Ce mois
        </button>
        <button type="button" className="bouton-texte" onClick={() => periode(3)}>
          3 derniers mois
        </button>
        <button type="button" className="bouton-texte" onClick={() => periode(12)}>
          12 derniers mois
        </button>
        <button type="button" className="bouton-texte" onClick={anneeEnCours}>
          Annee en cours
        </button>
      </div>

      <div className="export-ligne">
        <label className="ligne-champ">
          Du
          <input type="date" value={debut} onChange={(e) => setDebut(e.target.value)} />
        </label>
        <label className="ligne-champ">
          au
          <input type="date" value={fin} onChange={(e) => setFin(e.target.value)} />
        </label>
        <label className="ligne-interrupteur">
          <input
            type="checkbox"
            checked={echeances}
            onChange={(e) => setEcheances(e.target.checked)}
          />
          Inclure les echeances recurrentes
        </label>
      </div>

      <div className="export-actions">
        <button
          type="button"
          className="bouton"
          disabled={enCours !== ""}
          onClick={() => exporter("xlsx")}
        >
          {enCours === "xlsx" ? "Preparation..." : "Excel (.xlsx)"}
        </button>
        <button
          type="button"
          className="bouton secondaire"
          disabled={enCours !== ""}
          onClick={() => exporter("csv")}
        >
          {enCours === "csv" ? "Preparation..." : "CSV"}
        </button>
      </div>

      {erreur && <p className="message-erreur">{erreur}</p>}
    </div>
  );
}
