import { useEffect, useState } from "react";
import { api, LIBELLES_CATEGORIES, RegleRenommage } from "../api";

export default function Regles() {
  const [regles, setRegles] = useState<RegleRenommage[]>([]);
  const [erreur, setErreur] = useState("");

  const charger = () => api.get("/api/regles-renommage").then(setRegles);
  useEffect(() => {
    charger().catch((e) => setErreur(e.message));
  }, []);

  const supprimer = async (id: number) => {
    setErreur("");
    try {
      await api.supprimer("/api/regles-renommage/" + id);
      await charger();
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  return (
    <div>
      <h1 className="titre-page">Regles de renommage</h1>
      {erreur && <p className="message-erreur">{erreur}</p>}

      {regles.length === 0 && (
        <p className="vide">Aucune regle.</p>
      )}

      <ul className="liste-regles">
        {regles.map((r) => (
          <li key={r.id} className="carte-regle">
            <div className="carte-regle-tete">
              <strong>{r.libelleFinal}</strong>
              <span className="badge tag">{LIBELLES_CATEGORIES[r.categorie]}</span>
              <button className="bouton-texte danger" onClick={() => supprimer(r.id)}>
                Supprimer
              </button>
            </div>
            <p className="carte-regle-motif">motif : {r.motif}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
