import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import FeuilleBasse from "../components/FeuilleBasse";
import {
  api,
  CATEGORIES,
  ErreurApi,
  euros,
  LIBELLES_CATEGORIES,
  TransactionBancaire,
} from "../api";

type Statut = { connectee: boolean };

type ModeModal = "recurrent" | "ponctuel";

type FormulaireModal = {
  mode: ModeModal;
  libelleOriginal: string;
  libelle: string;
  montant: string;
  type: "DEBIT" | "CREDIT";
  frequence: "HEBDOMADAIRE" | "MENSUELLE" | "ANNUELLE";
  jourDuMois: string;
  categorie: string;
  date: string;
};

const NOMS_JOURS = ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"];
const NOMS_MOIS = ["janvier", "fevrier", "mars", "avril", "mai", "juin",
  "juillet", "aout", "septembre", "octobre", "novembre", "decembre"];

const jourLisible = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  return NOMS_JOURS[d.getDay()] + " " + d.getDate() + " " + NOMS_MOIS[d.getMonth()];
};

/**
 * Formate une Date en YYYY-MM-DD en LOCAL (evite le decalage UTC).
 * Ne PAS utiliser toISOString qui convertit en UTC.
 */
const formatLocal = (d: Date): string => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const j = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${j}`;
};

/** Prochaine occurrence en fonction de la frequence, sans piege de fuseau. */
const prochaineDate = (iso: string, frequence: string): string => {
  const [y, m, j] = iso.split("-").map(Number);
  const d = new Date(y, m - 1, j);
  if (frequence === "HEBDOMADAIRE") d.setDate(d.getDate() + 7);
  else if (frequence === "MENSUELLE") d.setMonth(d.getMonth() + 1);
  else d.setFullYear(d.getFullYear() + 1);
  return formatLocal(d);
};

export default function Import() {
  const [connectee, setConnectee] = useState<boolean | null>(null);
  const [jours, setJours] = useState(30);
  const [transactions, setTransactions] = useState<TransactionBancaire[]>([]);
  const [chargement, setChargement] = useState(false);
  const [traitees, setTraitees] = useState<Set<string>>(new Set());
  const [modal, setModal] = useState<FormulaireModal | null>(null);
  const [edition, setEdition] = useState<string | null>(null); // identifiant de la ligne en cours d edition
  const [libelleEdit, setLibelleEdit] = useState("");
  const [categorieEdit, setCategorieEdit] = useState("AUTRE");
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  useEffect(() => {
    api.get("/api/banque/statut")
      .then((s: Statut) => setConnectee(s.connectee))
      .catch(() => setConnectee(false));
  }, []);

  const charger = async (n: number) => {
    setChargement(true);
    setErreur("");
    setMessage("");
    try {
      const t = await api.get("/api/banque/transactions?jours=" + n);
      setTransactions(t);
      setTraitees(new Set());
    } catch (e) {
      setErreur(e instanceof ErreurApi ? e.message : "Erreur inattendue");
    } finally {
      setChargement(false);
    }
  };

  useEffect(() => {
    if (connectee) charger(jours);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectee]);

  const identifiant = (t: TransactionBancaire) =>
    t.reference ?? (t.date + "|" + t.libelle + "|" + t.montant + "|" + t.type);

  const restantes = useMemo(
    () => transactions.filter((t) => !traitees.has(identifiant(t))),
    [transactions, traitees]
  );

  // Groupement par date
  const parJour = useMemo(() => {
    const groupes = new Map<string, TransactionBancaire[]>();
    for (const t of restantes) {
      const liste = groupes.get(t.date) ?? [];
      liste.push(t);
      groupes.set(t.date, liste);
    }
    return Array.from(groupes.entries()).sort(([a], [b]) => b.localeCompare(a));
  }, [restantes]);

  const commencerEdition = (t: TransactionBancaire) => {
    setEdition(identifiant(t));
    setLibelleEdit(t.libelleAuto ?? t.libelle);
    setCategorieEdit(t.categorieAuto ?? "AUTRE");
  };

  const sauvegarderRegle = async () => {
    if (!edition) return;
    const source = transactions.find((t) => identifiant(t) === edition);
    if (!source) return;
    if (!libelleEdit.trim()) return;
    setErreur("");
    try {
      await api.post("/api/regles-renommage", {
        libelleOriginal: source.libelle,
        libelleFinal: libelleEdit.trim(),
        categorie: categorieEdit,
      });
      setEdition(null);
      setMessage("Regle enregistree. Elle sera appliquee aux prochains imports.");
      // Recharger pour propager la regle sur toutes les lignes similaires
      await charger(jours);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const marquerTraitee = (t: TransactionBancaire) => {
    const nouveau = new Set(traitees);
    nouveau.add(identifiant(t));
    setTraitees(nouveau);
  };

  const ouvrirModal = (t: TransactionBancaire, mode: ModeModal) => {
    setModal({
      mode,
      libelleOriginal: t.libelle,
      libelle: t.libelleAuto ?? t.libelle,
      montant: String(t.montant),
      type: t.type,
      frequence: "MENSUELLE",
      jourDuMois: String(new Date(t.date + "T00:00:00").getDate()),
      categorie: t.categorieAuto ?? "AUTRE",
      date: mode === "recurrent" ? prochaineDate(t.date, "MENSUELLE") : t.date,
    });
  };

  const changerFrequenceModal = (f: FormulaireModal["frequence"]) => {
    if (!modal || modal.mode !== "recurrent") return;
    // Recale la date sur la nouvelle periode
    const original = transactions.find((t) => t.libelle === modal.libelleOriginal);
    setModal({
      ...modal,
      frequence: f,
      date: original ? prochaineDate(original.date, f) : modal.date,
    });
  };

  const valider = async () => {
    if (!modal) return;
    setErreur("");
    try {
      if (modal.mode === "recurrent") {
        await api.post("/api/operations", {
          libelle: modal.libelle,
          montant: Number(modal.montant.replace(",", ".")),
          type: modal.type,
          frequence: modal.frequence,
          jourDuMois: modal.frequence === "MENSUELLE" ? Number(modal.jourDuMois) : null,
          categorie: modal.categorie,
          dateDebut: modal.date,
          active: true,
        });
      } else {
        await api.post("/api/transactions", {
          libelle: modal.libelle,
          montant: Number(modal.montant.replace(",", ".")),
          type: modal.type,
          categorie: modal.categorie,
          dateOperation: modal.date,
          debiteeEnBanque: true,
        });
      }

      // Apprentissage : si le libelle a change vs le libelle brut de la banque
      if (modal.libelle.trim() !== modal.libelleOriginal.trim()) {
        try {
          await api.post("/api/regles-renommage", {
            libelleOriginal: modal.libelleOriginal,
            libelleFinal: modal.libelle,
            categorie: modal.categorie,
          });
        } catch {
          /* Ok si le motif est trop generique, on ne bloque pas la creation */
        }
      }

      const origine = transactions.find((t) => t.libelle === modal.libelleOriginal
        && t.montant === Number(modal.montant.replace(",", "."))
        && t.type === modal.type);
      if (origine) marquerTraitee(origine);
      setMessage(modal.mode === "recurrent"
        ? "Ajoute aux operations recurrentes."
        : "Ajoute aux transactions ponctuelles.");
      setModal(null);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  if (connectee === null) return <p className="attente">Chargement…</p>;

  if (!connectee) {
    return (
      <div>
        <h1 className="titre-page">Import bancaire</h1>
        <p className="intro">
          Connecte d abord ta banque pour recuperer l historique de tes
          transactions et remplir tlprice avec tes vraies operations.
        </p>
        <Link to="/banque" className="bouton">Connecter ma banque</Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="titre-page">Import bancaire</h1>
      <p className="intro">
        Convertis tes transactions en recurrentes (loyer, salaire, abonnements)
        ou en ponctuelles. Quand tu renommes un libelle, tlprice retient la
        regle et l applique automatiquement aux prochains imports.
      </p>

      <div className="horizon-puces">
        <span className="etiquette">Periode</span>
        {[30, 60, 90].map((n) => (
          <button
            key={n}
            className={"puce" + (jours === n ? " active" : "")}
            onClick={() => { setJours(n); charger(n); }}
          >
            {n} jours
          </button>
        ))}
        <button className="bouton-texte" onClick={() => charger(jours)}>Actualiser</button>
        <Link to="/regles" className="bouton-texte">Gerer les regles</Link>
        {restantes.length > 0 && (
          <span className="etiquette">
            {restantes.length} / {transactions.length}
          </span>
        )}
      </div>

      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}
      {chargement && <p className="attente">Chargement des transactions…</p>}

      {!chargement && transactions.length === 0 && (
        <p className="vide">Aucune transaction sur cette periode.</p>
      )}

      {parJour.map(([date, liste]) => (
        <section key={date} className="groupe-jour">
          <h3 className="jour-titre">{jourLisible(date)}</h3>
          <ul className="liste-import">
            {liste.map((t) => {
              const libelleAffiche = t.libelleAuto ?? t.libelle;
              return (
                <li key={identifiant(t)} className="ligne-import">
                  {edition === identifiant(t) ? (
                    <span className="edition-inline">
                      <input
                        value={libelleEdit}
                        onChange={(e) => setLibelleEdit(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") sauvegarderRegle();
                          if (e.key === "Escape") setEdition(null);
                        }}
                        autoFocus
                      />
                      <select
                        value={categorieEdit}
                        onChange={(e) => setCategorieEdit(e.target.value)}
                      >
                        {CATEGORIES.map((c) => (
                          <option key={c} value={c}>{LIBELLES_CATEGORIES[c]}</option>
                        ))}
                      </select>
                      <button className="bouton-texte" onClick={sauvegarderRegle}>Enregistrer</button>
                      <button className="bouton-texte" onClick={() => setEdition(null)}>x</button>
                    </span>
                  ) : (
                    <span
                      className="mouvement-libelle libelle-cliquable"
                      title={t.libelle + " (clic pour renommer)"}
                      onClick={() => commencerEdition(t)}
                    >
                      {libelleAffiche}
                      {t.libelleAuto && <em className="badge auto">auto</em>}
                      {t.dejaImporte && <em className="badge deja">deja importe</em>}
                    </span>
                  )}
                  <span
                    className={
                      "mouvement-montant " +
                      (t.type === "DEBIT" ? "texte-debit" : "texte-credit")
                    }
                  >
                    {t.type === "DEBIT" ? "−" : "+"} {euros(t.montant)}
                  </span>
                  <span className="actions-import">
                    <button className="bouton petit" onClick={() => ouvrirModal(t, "recurrent")}>
                      Recurrent
                    </button>
                    <button className="bouton secondaire petit" onClick={() => ouvrirModal(t, "ponctuel")}>
                      Ponctuel
                    </button>
                    <button className="bouton-texte" onClick={() => marquerTraitee(t)}>
                      Ignorer
                    </button>
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      ))}

      {modal && (
        <FeuilleBasse
          onFermer={() => setModal(null)}
          titre={
            <>
              <p className="hero-eyebrow">
                {modal.mode === "recurrent" ? "Nouvelle operation recurrente" : "Nouvelle transaction ponctuelle"}
              </p>
              <p className="note">Libelle bancaire : <em>{modal.libelleOriginal}</em></p>
            </>
          }
        >
          <div className="formulaire">
            <label>
              Libelle
              <input
                value={modal.libelle}
                onChange={(e) => setModal({ ...modal, libelle: e.target.value })}
              />
              <em className="note-inline">
                Si tu le renommes, tlprice retiendra la regle et l appliquera aux
                futurs imports similaires.
              </em>
            </label>
            <label>
              Montant
              <input
                inputMode="decimal"
                value={modal.montant}
                onChange={(e) => setModal({ ...modal, montant: e.target.value })}
              />
            </label>
            <label>
              Sens
              <select
                value={modal.type}
                onChange={(e) => setModal({ ...modal, type: e.target.value as "DEBIT" | "CREDIT" })}
              >
                <option value="DEBIT">Debit (depense)</option>
                <option value="CREDIT">Credit (recette)</option>
              </select>
            </label>
            {modal.mode === "recurrent" && (
              <>
                <label>
                  Frequence
                  <select
                    value={modal.frequence}
                    onChange={(e) => changerFrequenceModal(e.target.value as FormulaireModal["frequence"])}
                  >
                    <option value="MENSUELLE">Mensuelle</option>
                    <option value="HEBDOMADAIRE">Hebdomadaire</option>
                    <option value="ANNUELLE">Annuelle</option>
                  </select>
                </label>
                {modal.frequence === "MENSUELLE" && (
                  <label>
                    Jour du mois
                    <input
                      type="number"
                      min={1}
                      max={31}
                      value={modal.jourDuMois}
                      onChange={(e) => setModal({ ...modal, jourDuMois: e.target.value })}
                    />
                  </label>
                )}
              </>
            )}
            <label>
              Categorie
              <select
                value={modal.categorie}
                onChange={(e) => setModal({ ...modal, categorie: e.target.value })}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{LIBELLES_CATEGORIES[c]}</option>
                ))}
              </select>
            </label>
            <label>
              {modal.mode === "recurrent" ? "Prochaine echeance" : "Date"}
              <input
                type="date"
                value={modal.date}
                onChange={(e) => setModal({ ...modal, date: e.target.value })}
              />
              {modal.mode === "recurrent" && (
                <em className="note-inline">
                  Pre-rempli apres l occurrence deja debitee pour eviter le
                  double comptage.
                </em>
              )}
            </label>
            <div className="actions-modal">
              <button className="bouton" onClick={valider}>
                Creer
              </button>
            </div>
          </div>
        </FeuilleBasse>
      )}
    </div>
  );
}
