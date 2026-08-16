import { FormEvent, useEffect, useState } from "react";
import { api, CATEGORIES, dateFr, euros, LIBELLES_CATEGORIES, formatLocal } from "../api";
import BlocExport from "../components/BlocExport";

type Transaction = {
  id: number;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
  categorie: string;
  dateOperation: string;
  debiteeEnBanque: boolean;
};

const vide = {
  libelle: "",
  montant: "",
  type: "DEBIT",
  categorie: "AUTRE",
  dateOperation: formatLocal(new Date()),
};

export default function Transactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [formulaire, setFormulaire] = useState({ ...vide });
  const [erreur, setErreur] = useState("");

  const charger = () => api.get("/api/transactions").then(setTransactions);
  useEffect(() => {
    charger().catch((e) => setErreur(e.message));
  }, []);

  const champ = (nom: string, valeur: string) =>
    setFormulaire({ ...formulaire, [nom]: valeur });

  const ajouter = async (e: FormEvent) => {
    e.preventDefault();
    setErreur("");
    try {
      await api.post("/api/transactions", {
        libelle: formulaire.libelle,
        montant: Number(formulaire.montant.replace(",", ".")),
        type: formulaire.type,
        categorie: formulaire.categorie,
        dateOperation: formulaire.dateOperation,
        debiteeEnBanque: false,
      });
      setFormulaire({ ...vide });
      await charger();
    } catch (err) {
      setErreur(err instanceof Error ? err.message : "Erreur");
    }
  };

  const basculer = async (id: number) => {
    await api.patch("/api/transactions/" + id + "/debitee");
    await charger();
  };

  const supprimer = async (id: number) => {
    await api.supprimer("/api/transactions/" + id);
    await charger();
  };

  const enAttente = transactions.filter((t) => !t.debiteeEnBanque);

  return (
    <div>
      <h1 className="titre-page">Ponctuel</h1>
      <p className="intro">
        Un achat carte pas encore visible sur ton solde banque ? Note-le ici :
        il est deduit de la projection jusqu a ce qu il apparaisse.
      </p>

      {/* Champs etiquetes plutot qu une rangee de placeholders : sur
          telephone les champs s empilent, et un placeholder disparait des
          qu on tape — on ne sait alors plus dans quelle case on ecrit. */}
      <form onSubmit={ajouter} className="formulaire-saisie">
        <label className="champ champ-large">
          <span>Libelle</span>
          <input
            placeholder="Courses"
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
          <span>Date</span>
          <input
            type="date"
            value={formulaire.dateOperation}
            onChange={(e) => champ("dateOperation", e.target.value)}
            required
          />
        </label>
        <button className="bouton bouton-plein">Ajouter</button>
      </form>
      {erreur && <p className="message-erreur">{erreur}</p>}

      {enAttente.length > 0 && (
        <p className="resume-attente">
          <strong>{enAttente.length}</strong>{" "}
          {enAttente.length > 1 ? "transactions attendent" : "transaction attend"} d apparaitre
          sur ton compte.
        </p>
      )}

      <ul className="liste-cartes">
        {transactions.map((t) => (
          <li key={t.id} className={"carte-ligne" + (t.debiteeEnBanque ? " inactive" : "")}>
            <span className="mouvement-date">{dateFr(t.dateOperation)}</span>
            <span className="mouvement-libelle">
              {t.libelle}
              {!t.debiteeEnBanque && <em className="badge">pas encore debitee</em>}
            </span>
            <span
              className={
                "mouvement-montant " +
                (t.type === "DEBIT" ? "texte-debit" : "texte-credit")
              }
            >
              {t.type === "DEBIT" ? "−" : "+"} {euros(t.montant)}
            </span>
            <span className="actions-ligne">
              <button className="bouton-texte" onClick={() => basculer(t.id)}>
                {t.debiteeEnBanque ? "Marquer non debitee" : "Marquer debitee"}
              </button>
              <button className="bouton-texte danger" onClick={() => supprimer(t.id)}>
                Supprimer
              </button>
            </span>
          </li>
        ))}
        {transactions.length === 0 && (
          <p className="vide">
            Aucune transaction pour l instant. Ajoute un achat des que tu le
            fais : c est ce qui empeche la projection de compter deux fois.
          </p>
        )}
      </ul>

      {/* L export est un outil, pas l action principale de cet ecran : il
          passe apres la liste au lieu de la repousser sous la ligne de
          flottaison. */}
      <BlocExport />
    </div>
  );
}
