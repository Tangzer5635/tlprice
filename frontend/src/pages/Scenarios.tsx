import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, dateFr, euros, formatLocal } from "../api";
import CourbeComparaison from "../components/CourbeComparaison";
import {
  ajouterMois,
  deplier,
  LigneApi,
  LigneEdition,
  MouvementSimple,
  montantNombre,
  nouvelleCle,
  replier,
  trajectoires,
  verdict,
} from "../scenarioCalcul";

type Scenario = { id: number; nom: string; lignes: LigneApi[] };
type Projection = { soldeReel: number; dateMaj: string; mouvements: MouvementSimple[] };

const HORIZONS = [
  { libelle: "1 mois", mois: 1 },
  { libelle: "3 mois", mois: 3 },
  { libelle: "6 mois", mois: 6 },
  { libelle: "1 an", mois: 12 },
];

const REPETITIONS = [1, 2, 3, 4, 6, 10, 12, 18, 24, 36, 48, 60];

const aujourdhui = () => formatLocal(new Date());

const ligne = (type: "DEBIT" | "CREDIT", repetition = 1, libelle = ""): LigneEdition => ({
  cle: nouvelleCle(),
  libelle,
  montant: "",
  type,
  categorie: type === "CREDIT" ? "REVENUS" : "AUTRE",
  date: aujourdhui(),
  repetition,
});

/** Montant sans centimes : dans une tuile etroite, c est l ordre de grandeur qui compte. */
const rond = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 0 }).format(n);

const COULEURS = { ok: "var(--credit)", seuil: "var(--alerte-bord)", decouvert: "var(--debit)" };

export default function Scenarios() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selection, setSelection] = useState<number | null>(null);
  const [nom, setNom] = useState("");
  const [lignes, setLignes] = useState<LigneEdition[]>([ligne("DEBIT")]);
  const [reference, setReference] = useState("");
  const [projection, setProjection] = useState<Projection | null>(null);
  const [seuil, setSeuil] = useState<number | null>(null);
  const [horizonMois, setHorizonMois] = useState<number | null>(null);
  const [erreur, setErreur] = useState("");
  const [enCours, setEnCours] = useState(false);

  const hypotheses = useMemo(() => deplier(lignes), [lignes]);
  const signature = JSON.stringify([nom.trim(), hypotheses]);
  const modifie = signature !== reference;

  // Horizon auto : 3 mois, ou au-dela du dernier mouvement + 1 mois.
  const horizon = useMemo(() => {
    if (horizonMois !== null) return ajouterMois(aujourdhui(), horizonMois);
    const dernier = hypotheses.reduce((d, h) => (h.date > d ? h.date : d), aujourdhui());
    const auto = ajouterMois(dernier, 1);
    const min = ajouterMois(aujourdhui(), 3);
    const max = ajouterMois(aujourdhui(), 60);
    return auto < min ? min : auto > max ? max : auto;
  }, [horizonMois, hypotheses]);

  const chargerListe = () =>
    api.get("/api/scenarios").then((l: Scenario[]) => {
      setScenarios(l);
      return l;
    });

  useEffect(() => {
    chargerListe()
      .then((l) => ouvrir(l[0] ?? null))
      .catch((e) => {
        setErreur(e.message);
        ouvrir(null);
      });
    api.get("/api/parametres/alerte").then((a) => setSeuil(a?.seuil ?? null)).catch(() => {});
  }, []);

  useEffect(() => {
    api.get("/api/solde/projete?date=" + horizon).then(setProjection).catch((e) => setErreur(e.message));
  }, [horizon]);

  const ouvrir = (s: Scenario | null) => {
    setErreur("");
    if (s === null) {
      setSelection(null);
      setNom("");
      setLignes([ligne("DEBIT")]);
      setReference(JSON.stringify(["", []]));
      return;
    }
    const replies = replier(s.lignes);
    setSelection(s.id);
    setNom(s.nom);
    setLignes(replies.length ? replies : [ligne("DEBIT")]);
    setReference(JSON.stringify([s.nom.trim(), deplier(replies)]));
  };

  const maj = (cle: string, modif: Partial<LigneEdition>) =>
    setLignes((l) => l.map((x) => (x.cle === cle ? { ...x, ...modif } : x)));

  const enregistrer = async () => {
    setErreur("");
    if (!nom.trim()) return setErreur("Donne un nom.");
    if (hypotheses.length === 0) return setErreur("Ajoute un montant et un libelle.");
    setEnCours(true);
    try {
      const corps = { nom: nom.trim(), lignes: hypotheses };
      const s: Scenario =
        selection === null
          ? await api.post("/api/scenarios", corps)
          : await api.put("/api/scenarios/" + selection, corps);
      const liste = await chargerListe();
      ouvrir(liste.find((x) => x.id === s.id) ?? s);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setEnCours(false);
    }
  };

  const supprimer = async () => {
    if (selection === null || !window.confirm("Supprimer « " + nom + " » ?")) return;
    try {
      await api.supprimer("/api/scenarios/" + selection);
      const liste = await chargerListe();
      ouvrir(liste[0] ?? null);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const points = useMemo(
    () =>
      projection
        ? trajectoires(projection.soldeReel, projection.dateMaj, horizon, projection.mouvements, hypotheses)
        : [],
    [projection, horizon, hypotheses]
  );
  const v = points.length > 1 ? verdict(points, seuil) : null;

  // Chronologie : une etape par ligne saisie (une mensualite = une etape),
  // avec le solde juste apres sa derniere occurrence.
  const chronologie = useMemo(() => {
    const parDate = new Map(points.map((p) => [p.date, p]));
    return lignes
      .filter((l) => l.libelle.trim() && montantNombre(l.montant) > 0 && l.date)
      .map((l) => {
        const fin = ajouterMois(l.date, l.repetition - 1);
        return {
          cle: l.cle,
          libelle: l.libelle.trim(),
          type: l.type,
          montant: montantNombre(l.montant),
          repetition: l.repetition,
          date: l.date,
          fin,
          apres: parDate.get(fin)?.avec ?? null,
        };
      })
      .sort((a, b) => a.date.localeCompare(b.date));
  }, [lignes, points]);

  const couleur = v ? COULEURS[v.niveau] : "var(--accent)";
  const ecart = v ? v.finAvec - v.finReel : 0;
  const coutTotal = hypotheses.reduce((t, h) => t + (h.type === "CREDIT" ? h.montant : -h.montant), 0);

  return (
    <div className="simulateur">
      <h1 className="titre-page">Et si…</h1>

      <div className="horizon-puces onglets-scenarios">
        {scenarios.map((s) => (
          <button
            key={s.id}
            className={"puce" + (selection === s.id ? " active" : "")}
            onClick={() => ouvrir(s)}
          >
            {s.nom}
          </button>
        ))}
        <button className={"puce puce-nouveau" + (selection === null ? " active" : "")} onClick={() => ouvrir(null)}>
          ＋ Nouveau
        </button>
      </div>

      {/* ---- Verdict ---- */}
      {v && (
        <section className={"verdict verdict-" + v.niveau}>
          <div className="verdict-tete">
            <span className="verdict-icone" aria-hidden="true">
              {v.niveau === "ok" ? "✓" : v.niveau === "seuil" ? "!" : "✕"}
            </span>
            <div>
              <p className="verdict-titre">
                {hypotheses.length === 0
                  ? "Ajoute une depense"
                  : v.niveau === "ok"
                    ? "Ca passe"
                    : v.niveau === "seuil"
                      ? "Sous ton seuil le " + dateFr(v.date!)
                      : "Decouvert le " + dateFr(v.date!)}
              </p>
              {hypotheses.length > 0 && (
                <p className="verdict-sous">
                  {v.niveau === "ok"
                    ? "Marge au plus bas : " + rond(v.marge)
                    : v.dejaSansScenario
                      ? "Deja le cas sans ce scenario"
                      : "A cause de ce scenario"}
                </p>
              )}
            </div>
          </div>

          <div className="hero-bandes">
            <span className="bande">
              <em>Sans</em>
              <strong>{rond(v.finReel)}</strong>
              <i>au {dateFr(horizon)}</i>
            </span>
            <span className="bande">
              <em>Avec</em>
              <strong style={{ color: couleur }}>{rond(v.finAvec)}</strong>
              <i>
                {ecart < 0 ? "−" : "+"} {rond(Math.abs(ecart))}
              </i>
            </span>
            <span className="bande">
              <em>Point bas</em>
              <strong className={v.pointBas < 0 ? "texte-debit" : ""}>{rond(v.pointBas)}</strong>
              <i>{dateFr(v.datePointBas)}</i>
            </span>
          </div>
        </section>
      )}

      {/* ---- Courbes ---- */}
      <section className="hero">
        <div className="horizon-puces" role="group" aria-label="Horizon">
          {HORIZONS.map((h) => (
            <button
              key={h.mois}
              className={"puce" + (horizonMois === h.mois ? " active" : "")}
              onClick={() => setHorizonMois(horizonMois === h.mois ? null : h.mois)}
            >
              {h.libelle}
            </button>
          ))}
        </div>
        {points.length > 1 ? (
          <CourbeComparaison points={points} seuil={seuil} evenements={hypotheses} couleur={couleur} />
        ) : (
          <div className="squelette squelette-courbe" />
        )}
      </section>

      {/* ---- Edition ---- */}
      <section className="carte-parametre editeur-scenario">
        <input
          className="nom-scenario"
          placeholder="Nom : velo, vacances, voiture…"
          value={nom}
          onChange={(e) => setNom(e.target.value)}
          maxLength={120}
          aria-label="Nom du scenario"
        />

        <ul className="lignes-hypotheses">
          {lignes.map((l) => (
            <li key={l.cle} className={"hypothese " + (l.type === "DEBIT" ? "est-debit" : "est-credit")}>
              <div className="hypothese-ligne">
                <button
                  type="button"
                  className="sens"
                  onClick={() => maj(l.cle, { type: l.type === "DEBIT" ? "CREDIT" : "DEBIT" })}
                  aria-label={l.type === "DEBIT" ? "Depense (toucher pour rentree)" : "Rentree (toucher pour depense)"}
                >
                  {l.type === "DEBIT" ? "−" : "+"}
                </button>
                <input
                  className="hypothese-montant"
                  inputMode="decimal"
                  placeholder="0 €"
                  value={l.montant}
                  onChange={(e) => maj(l.cle, { montant: e.target.value })}
                  aria-label="Montant"
                />
                <input
                  className="hypothese-libelle"
                  placeholder="Quoi ?"
                  value={l.libelle}
                  onChange={(e) => maj(l.cle, { libelle: e.target.value })}
                  aria-label="Libelle"
                />
                <button
                  type="button"
                  className="retirer"
                  onClick={() => setLignes((x) => (x.length > 1 ? x.filter((y) => y.cle !== l.cle) : [ligne("DEBIT")]))}
                  aria-label="Retirer"
                >
                  ✕
                </button>
              </div>
              <div className="hypothese-ligne hypothese-quand">
                <input
                  type="date"
                  value={l.date}
                  onChange={(e) => maj(l.cle, { date: e.target.value })}
                  aria-label="Date"
                />
                <select
                  value={l.repetition}
                  onChange={(e) => maj(l.cle, { repetition: Number(e.target.value) })}
                  aria-label="Repetition"
                >
                  {REPETITIONS.map((n) => (
                    <option key={n} value={n}>
                      {n === 1 ? "Une fois" : n + " mois"}
                    </option>
                  ))}
                </select>
                {l.repetition > 1 && montantNombre(l.montant) > 0 && (
                  <span className="hypothese-total">= {euros(montantNombre(l.montant) * l.repetition)}</span>
                )}
              </div>
            </li>
          ))}
        </ul>

        <div className="ajouts">
          <button type="button" className="puce" onClick={() => setLignes((x) => [...x, ligne("DEBIT")])}>
            − Depense
          </button>
          <button type="button" className="puce" onClick={() => setLignes((x) => [...x, ligne("CREDIT")])}>
            + Rentree
          </button>
          <button type="button" className="puce" onClick={() => setLignes((x) => [...x, ligne("DEBIT", 12, "Mensualite")])}>
            ↻ Mensualite
          </button>
        </div>

        {erreur && <p className="message-erreur">{erreur}</p>}

        <div className="hero-actions">
          <button className="bouton" onClick={enregistrer} disabled={enCours || !modifie}>
            {selection === null ? "Enregistrer" : modifie ? "Enregistrer" : "Enregistre ✓"}
          </button>
          {selection !== null && !modifie && (
            <Link className="bouton secondaire" to={"/?scenario=" + selection}>
              Voir sur Solde
            </Link>
          )}
          {selection !== null && (
            <button className="bouton-texte danger" onClick={supprimer}>
              Supprimer
            </button>
          )}
        </div>
      </section>

      {/* ---- Au fil des jours ---- */}
      {chronologie.length > 0 && (
        <section className="regle">
          <h2 className="titre-section">
            Au fil des jours{" "}
            <span className={coutTotal < 0 ? "texte-debit" : "texte-credit"}>
              {coutTotal < 0 ? "−" : "+"} {euros(Math.abs(coutTotal))}
            </span>
          </h2>
          <ol className="chronologie">
            {chronologie.map((c) => {
              const sous = c.apres !== null && (c.apres < 0 || (seuil != null && c.apres < seuil));
              return (
                <li key={c.cle} className="chrono-etape">
                  <span className="chrono-date">
                    {dateFr(c.date)}
                    {c.repetition > 1 && <> → {dateFr(c.fin)}</>}
                  </span>
                  <span className="chrono-libelle">{c.libelle}</span>
                  <span className={"chrono-montant " + (c.type === "DEBIT" ? "texte-debit" : "texte-credit")}>
                    {c.repetition > 1 && c.repetition + " × "}
                    {c.type === "DEBIT" ? "−" : "+"} {euros(c.montant)}
                  </span>
                  <span className={"chrono-solde" + (sous ? " chrono-alerte" : "")}>
                    {c.apres === null ? "—" : euros(c.apres)}
                  </span>
                </li>
              );
            })}
          </ol>
        </section>
      )}
    </div>
  );
}
