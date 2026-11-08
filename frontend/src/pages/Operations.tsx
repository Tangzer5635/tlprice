import { FormEvent, useEffect, useState } from "react";
import { api, CATEGORIES, euros, LIBELLES_CATEGORIES, formatLocal } from "../api";

type Operation = {
  id: number;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
  frequence: "HEBDOMADAIRE" | "MENSUELLE" | "ANNUELLE";
  jourDuMois: number | null;
  categorie: string;
  dateDebut: string;
  dateFin: string | null;
  active: boolean;
};

const vide = {
  libelle: "",
  montant: "",
  type: "DEBIT",
  frequence: "MENSUELLE",
  jourDuMois: "1",
  categorie: "AUTRE",
  dateDebut: formatLocal(new Date()),
};

export default function Operations() {
  const [operations, setOperations] = useState<Operation[]>([]);
  const [formulaire, setFormulaire] = useState({ ...vide });
  const [erreur, setErreur] = useState("");

  const charger = () => api.get("/api/operations").then(setOperations);
  useEffect(() => {
    charger().catch((e) => setErreur(e.message));
  }, []);

  const champ = (nom: string, valeur: string) =>
    setFormulaire({ ...formulaire, [nom]: valeur });

  const ajouter = async (e: FormEvent) => {
    e.preventDefault();
    setErreur("");
    try {
      await api.post("/api/operations", {
        libelle: formulaire.libelle,
        montant: Number(formulaire.montant.replace(",", ".")),
        type: formulaire.type,
        frequence: formulaire.frequence,
        jourDuMois:
          formulaire.frequence === "MENSUELLE" ? Number(formulaire.jourDuMois) : null,
        categorie: formulaire.categorie,
        dateDebut: formulaire.dateDebut,
        active: true,
      });
      setFormulaire({ ...vide });
      await charger();
    } catch (err) {
      setErreur(err instanceof Error ? err.message : "Erreur");
    }
  };

  const basculer = async (op: Operation) => {
    await api.put("/api/operations/" + op.id, { ...op, active: !op.active });
    await charger();
  };

  /**
   * Supprimer emporte les ajustements ponctuels et l historique de controle
   * de cette operation. On le dit avant, chiffres a l appui, plutot que de
   * laisser decouvrir la perte apres coup.
   */
  const supprimer = async (id: number) => {
    setErreur("");
    try {
      const impact = await api.get("/api/operations/" + id + "/impact");
      const pertes: string[] = [];
      if (impact.ajustements > 0) {
        pertes.push(
          impact.ajustements + (impact.ajustements > 1 ? " ajustements ponctuels" : " ajustement ponctuel")
        );
      }
      if (impact.controles > 0) {
        pertes.push(
          impact.controles + (impact.controles > 1 ? " lignes de controle" : " ligne de controle")
        );
      }
      const question = pertes.length === 0
        ? "Supprimer « " + impact.libelle + " » ?"
        : "Supprimer « " + impact.libelle + " » emportera aussi "
          + pertes.join(" et ")
          + ".\n\nPour garder cet historique, utilise plutot Suspendre : "
          + "l operation cesse de generer des echeances et son passe reste consultable.\n\nSupprimer quand meme ?";
      if (!window.confirm(question)) return;
    } catch {
      // Impact indisponible : on demande une confirmation simple plutot que
      // de bloquer la suppression.
      if (!window.confirm("Supprimer cette operation ?")) return;
    }
    await api.supprimer("/api/operations/" + id);
    await charger();
  };

  return (
    <div>
      <h1 className="titre-page">Operations recurrentes</h1>

      {/* Champs etiquetes, comme l onglet Ponctuel : sur telephone ils
          s empilent en deux colonnes, et on sait dans quelle case on ecrit
          meme apres avoir commence a taper. */}
      <form onSubmit={ajouter} className="formulaire-saisie">
        <label className="champ champ-large">
          <span>Libelle</span>
          <input
            placeholder="Loyer"
            value={formulaire.libelle}
            onChange={(e) => champ("libelle", e.target.value)}
            required
          />
        </label>
        <label className="champ">
          <span>Montant</span>
          <input
            placeholder="0,00"
            inputMode="decimal"
            value={formulaire.montant}
            onChange={(e) => champ("montant", e.target.value)}
            required
          />
        </label>
        <label className="champ">
          <span>Sens</span>
          <select value={formulaire.type} onChange={(e) => champ("type", e.target.value)}>
            <option value="DEBIT">Debit</option>
            <option value="CREDIT">Credit</option>
          </select>
        </label>
        <label className="champ">
          <span>Frequence</span>
          <select
            value={formulaire.frequence}
            onChange={(e) => champ("frequence", e.target.value)}
          >
            <option value="MENSUELLE">Mensuel</option>
            <option value="HEBDOMADAIRE">Hebdomadaire</option>
            <option value="ANNUELLE">Annuel</option>
          </select>
        </label>
        {formulaire.frequence === "MENSUELLE" && (
          <label className="champ">
            <span>Jour du mois</span>
            <input
              type="number"
              min={1}
              max={31}
              value={formulaire.jourDuMois}
              onChange={(e) => champ("jourDuMois", e.target.value)}
              required
            />
          </label>
        )}
        <label className="champ">
          <span>Categorie</span>
          <select
            value={formulaire.categorie}
            onChange={(e) => champ("categorie", e.target.value)}
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {LIBELLES_CATEGORIES[c]}
              </option>
            ))}
          </select>
        </label>
        <label className="champ">
          <span>Premiere echeance</span>
          <input
            type="date"
            value={formulaire.dateDebut}
            onChange={(e) => champ("dateDebut", e.target.value)}
            required
          />
        </label>
        <button className="bouton bouton-plein">Ajouter</button>
      </form>
      {erreur && <p className="message-erreur">{erreur}</p>}

      <ul className="liste-cartes">
        {operations.map((op) => (
          <li key={op.id} className={"carte-ligne" + (op.active ? "" : " inactive")}>
            <span className="mouvement-libelle">{op.libelle}</span>
            <span className="detail-frequence">
              {op.frequence === "MENSUELLE" && "le " + op.jourDuMois + " du mois"}
              {op.frequence === "HEBDOMADAIRE" && "chaque semaine"}
              {op.frequence === "ANNUELLE" && "chaque annee"}
              {" · "}
              {LIBELLES_CATEGORIES[op.categorie]}
            </span>
            <span
              className={
                "mouvement-montant " +
                (op.type === "DEBIT" ? "texte-debit" : "texte-credit")
              }
            >
              {op.type === "DEBIT" ? "−" : "+"} {euros(op.montant)}
            </span>
            <span className="actions-ligne">
              <button className="bouton-texte" onClick={() => basculer(op)}>
                {op.active ? "Suspendre" : "Reactiver"}
              </button>
              <button className="bouton-texte danger" onClick={() => supprimer(op.id)}>
                Supprimer
              </button>
            </span>
          </li>
        ))}
        {operations.length === 0 && (
          <p className="vide">Aucune operation.</p>
        )}
      </ul>
    </div>
  );
}
