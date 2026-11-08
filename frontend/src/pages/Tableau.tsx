import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, AlerteSolde, dateFr, euros, formatLocal } from "../api";
import CourbeSolde, { PointPasse } from "../components/CourbeSolde";
import RepartitionCategories from "../components/RepartitionCategories";
import TirerPourRafraichir from "../components/TirerPourRafraichir";

type Mouvement = {
  date: string;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
  categorie: string | null;
  origine: "ECHEANCE" | "TRANSACTION";
};

type Projection = {
  soldeReel: number;
  dateMaj: string;
  dateProjection: string;
  totalDebits: number;
  totalCredits: number;
  soldeProjete: number;
  mouvements: Mouvement[];
};

const finDeMois = () => {
  const d = new Date();
  return formatLocal(new Date(d.getFullYear(), d.getMonth() + 1, 0));
};

const dansNJours = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return formatLocal(d);
};

/** Libelle relatif : "aujourd hui", "demain", puis la date. */
const jourRelatif = (iso: string) => {
  const ecart = Math.round(
    (new Date(iso + "T00:00:00").getTime() -
      new Date(formatLocal(new Date()) + "T00:00:00").getTime()) / 86400000
  );
  if (ecart === 0) return "aujourd hui";
  if (ecart === 1) return "demain";
  return new Date(iso + "T00:00:00").toLocaleDateString("fr-FR", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
};

export default function Tableau() {
  const [projection, setProjection] = useState<Projection | null>(null);
  const [alerte, setAlerte] = useState<AlerteSolde | null>(null);
  const [date, setDate] = useState(finDeMois());
  const [connectee, setConnectee] = useState(false);
  const [sync, setSync] = useState(false);
  const [message, setMessage] = useState("");
  const [soldeManuel, setSoldeManuel] = useState("");
  const [editionSolde, setEditionSolde] = useState(false);
  const [ecarts, setEcarts] = useState(0);
  const [historique, setHistorique] = useState<PointPasse[]>([]);
  const [fiabilite, setFiabilite] = useState<any>(null);
  const [scenarios, setScenarios] = useState<{ id: number; nom: string }[]>([]);
  // ?scenario=ID : ouvert depuis le simulateur, le scenario est deja applique.
  const [params] = useSearchParams();
  const [scenarioActif, setScenarioActif] = useState<number | null>(
    params.get("scenario") ? Number(params.get("scenario")) : null
  );
  // Projection reelle conservee a part : c est la comparaison qui a du sens,
  // pas le chiffre du scenario tout seul.
  const [projectionReelle, setProjectionReelle] = useState<Projection | null>(null);
  const [capacite, setCapacite] = useState<any>(null);

  const charger = useCallback(async (d: string, scenario: number | null) => {
    const reelle = await api.get("/api/solde/projete?date=" + d);
    setProjectionReelle(reelle);
    if (scenario === null) {
      setProjection(reelle);
    } else {
      setProjection(await api.get("/api/solde/projete?date=" + d + "&scenario=" + scenario));
    }
  }, []);

  useEffect(() => {
    charger(date, scenarioActif).catch((e) => setMessage(e.message));
    api.get("/api/solde/capacite-epargne?date=" + date).then(setCapacite).catch(() => {});
    api.get("/api/banque/statut").then((s) => setConnectee(s.connectee)).catch(() => {});
    api.get("/api/parametres/alerte").then(setAlerte).catch(() => {});
    api.get("/api/rapprochement/en-attente")
      .then((r) => setEcarts(r.nombre))
      .catch(() => {});
    api.get("/api/historique?jours=45").then(setHistorique).catch(() => {});
    api.get("/api/historique/fiabilite").then(setFiabilite).catch(() => {});
    api.get("/api/scenarios")
      .then((l) => setScenarios(l.map((s: any) => ({ id: s.id, nom: s.nom }))))
      .catch(() => {});
  }, [date, scenarioActif, charger]);

  const synchroniser = useCallback(async () => {
    setSync(true);
    setMessage("");
    try {
      await api.post("/api/banque/synchroniser");
      await charger(date, scenarioActif);
      setMessage("Solde synchronise avec ta banque.");
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Erreur");
    } finally {
      setSync(false);
    }
  }, [charger, date, scenarioActif]);

  const enregistrerSolde = async () => {
    try {
      await api.put("/api/solde", { solde: Number(soldeManuel.replace(",", ".")) });
      setEditionSolde(false);
      await charger(date, scenarioActif);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Erreur");
    }
  };

  /**
   * Les mouvements groupes par jour. Une liste plate de quarante lignes se
   * lit mal sur un telephone : on ne voit plus quel jour est charge, alors
   * que c est justement la question qu on se pose en regardant l ecran.
   */
  const parJour = useMemo(() => {
    if (!projection) return [];
    const groupes = new Map<string, Mouvement[]>();
    for (const m of projection.mouvements) {
      const liste = groupes.get(m.date);
      if (liste) liste.push(m);
      else groupes.set(m.date, [m]);
    }
    return Array.from(groupes.entries()).map(([jour, mouvements]) => {
      let net = 0;
      for (const m of mouvements) net += m.type === "DEBIT" ? -m.montant : m.montant;
      return { jour, mouvements, net };
    });
  }, [projection]);

  /* Squelette de chargement plutot qu un mot seul : la page ne saute pas. */
  if (!projection) {
    return (
      <div aria-busy="true" aria-label="Chargement du solde">
        <section className="hero">
          <div className="squelette squelette-eyebrow" />
          <div className="squelette squelette-montant" />
          <div className="squelette squelette-ligne" />
        </section>
        <div className="squelette squelette-courbe" />
      </div>
    );
  }

  const negatif = projection.soldeProjete < 0;

  const horizons = [
    { libelle: "7 jours", valeur: dansNJours(7) },
    { libelle: "Fin du mois", valeur: finDeMois() },
    { libelle: "30 jours", valeur: dansNJours(30) },
    { libelle: "90 jours", valeur: dansNJours(90) },
    { libelle: "12 mois", valeur: dansNJours(365) },
  ];

  return (
    <TirerPourRafraichir onRafraichir={synchroniser} actif={connectee}>
      {alerte && alerte.alerte && (
        <div className="bandeau-alerte">
          ⚠ <strong>{euros(alerte.soldeMinimum ?? 0)}</strong>
          {alerte.dateMinimum && " le " + dateFr(alerte.dateMinimum)}
          {" · seuil " + euros(alerte.seuil ?? 0)}
        </div>
      )}
      {ecarts > 0 && (
        <Link to="/controle" className="bandeau-controle">
          <strong>
            {ecarts} {ecarts > 1 ? "ecarts" : "ecart"} a verifier
          </strong>
          <span>Verifier →</span>
        </Link>
      )}

      <section className="hero">
        <p className="hero-eyebrow">
          Ce qu il te restera le {dateFr(projection.dateProjection)}
        </p>
        <p className={"hero-montant" + (negatif ? " negatif" : "")}>
          {euros(projection.soldeProjete)}
        </p>
        {/* L horizon commande le chiffre du dessus : sa place est ici, et
            non en bas de page comme jusqu a la v2.4, ou il fallait faire
            defiler pour trouver le reglage qui changeait le titre. */}
        <div className="horizon-puces" role="group" aria-label="Horizon de projection">
          {horizons.map((h) => (
            <button
              key={h.libelle}
              className={"puce" + (date === h.valeur ? " active" : "")}
              onClick={() => setDate(h.valeur)}
            >
              {h.libelle}
            </button>
          ))}
          <input
            type="date"
            className="horizon-date"
            value={date}
            min={formatLocal(new Date())}
            onChange={(e) => setDate(e.target.value)}
            aria-label="Autre date"
          />
        </div>

        {scenarios.length > 0 && (
          <div className="scenario-barre" role="group" aria-label="Scenario">
            <div className="horizon-puces">
              <button
                className={"puce" + (scenarioActif === null ? " active" : "")}
                onClick={() => setScenarioActif(null)}
              >
                Reel
              </button>
              {scenarios.map((s) => (
                <button
                  key={s.id}
                  className={"puce puce-scenario" + (scenarioActif === s.id ? " active" : "")}
                  onClick={() => setScenarioActif(scenarioActif === s.id ? null : s.id)}
                >
                  {s.nom}
                </button>
              ))}
            </div>
            {scenarioActif !== null && projectionReelle && (
              <p className="scenario-compare">
                Reel <strong>{euros(projectionReelle.soldeProjete)}</strong>
                {" → "}
                <span
                  className={
                    projection.soldeProjete < projectionReelle.soldeProjete
                      ? "texte-debit"
                      : "texte-credit"
                  }
                >
                  {projection.soldeProjete < projectionReelle.soldeProjete ? "−" : "+"}{" "}
                  {euros(Math.abs(projection.soldeProjete - projectionReelle.soldeProjete))}
                </span>
              </p>
            )}
          </div>
        )}

        <div className="hero-bandes">
          <span className="bande">
            <em>Banque</em>
            <strong>{euros(projection.soldeReel)}</strong>
            <i>au {dateFr(projection.dateMaj)}</i>
          </span>
          <span className="bande">
            <em>Entrees</em>
            <strong className="texte-credit">+ {euros(projection.totalCredits)}</strong>
          </span>
          <span className="bande">
            <em>Sorties</em>
            <strong className="texte-debit">− {euros(projection.totalDebits)}</strong>
          </span>
        </div>

        <div className="hero-actions">
          {connectee ? (
            <>
              <button className="bouton masquer-mobile" onClick={synchroniser} disabled={sync}>
                {sync ? "Synchronisation…" : "Synchroniser avec ma banque"}
              </button>
            </>
          ) : (
            <Link to="/banque" className="bouton secondaire">
              Connecter ma banque
            </Link>
          )}
          {/* Quand la banque est branchee, le solde lui appartient : le
              corriger ici serait ecrase a la synchro suivante. On renvoie
              vers les reglages, ou l avertissement peut etre explique. */}
          {connectee ? (
            <Link to="/parametres" className="bouton-texte">
              Corriger le solde
            </Link>
          ) : editionSolde ? (
            <span className="edition-solde">
              <input
                type="text"
                inputMode="decimal"
                placeholder="0,00"
                value={soldeManuel}
                onChange={(e) => setSoldeManuel(e.target.value)}
                autoFocus
              />
              <button className="bouton-texte" onClick={enregistrerSolde}>
                Enregistrer
              </button>
              <button className="bouton-texte" onClick={() => setEditionSolde(false)}>
                Annuler
              </button>
            </span>
          ) : (
            <button
              className="bouton-texte"
              onClick={() => {
                setSoldeManuel(String(projection.soldeReel));
                setEditionSolde(true);
              }}
            >
              Corriger le solde a la main
            </button>
          )}
        </div>
        {message && <p className="message-info">{message}</p>}

        <CourbeSolde
          soldeReel={projection.soldeReel}
          dateMaj={projection.dateMaj}
          dateProjection={projection.dateProjection}
          mouvements={projection.mouvements}
          seuil={alerte ? alerte.seuil : null}
          historique={historique}
        />

        {fiabilite && fiabilite.ecart !== null && (
          <p className="fiabilite">
            Prevu il y a 1 mois <strong>{euros(fiabilite.annonce)}</strong>
            {" · "}reel <strong>{euros(fiabilite.constate)}</strong>{" "}
            <span className={fiabilite.ecart >= 0 ? "texte-credit" : "texte-debit"}>
              ({fiabilite.ecart >= 0 ? "+" : "−"} {euros(Math.abs(fiabilite.ecart))})
            </span>
          </p>
        )}
      </section>

      {capacite && (
        <section className="carte-epargne">
          <p className="hero-eyebrow">Capacite d epargne</p>
          <p className="epargne-montant">{euros(capacite.capacite)}</p>
          <div className="hero-bandes">
            <span className="bande">
              <em>Point bas</em>
              <strong>{euros(capacite.creux)}</strong>
              <i>{dateFr(capacite.dateCreux)}</i>
            </span>
            <span className="bande">
              <em>Seuil</em>
              <strong>{euros(capacite.seuil)}</strong>
            </span>
            <span className="bande">
              <em>Jusqu au</em>
              <strong>{dateFr(capacite.horizon)}</strong>
            </span>
          </div>
        </section>
      )}

      <RepartitionCategories mouvements={projection.mouvements} />

      <section className="regle">
        <h2 className="titre-section">D ici la</h2>
        {parJour.length === 0 ? (
          <p className="vide">Aucun mouvement prevu.</p>
        ) : (
          <div className="jours">
            {parJour.map((groupe) => (
              <section key={groupe.jour} className="jour-bloc">
                <h3 className="jour-tete">
                  <span className="jour-nom">{jourRelatif(groupe.jour)}</span>
                  <span
                    className={"jour-net " + (groupe.net < 0 ? "texte-debit" : "texte-credit")}
                  >
                    {groupe.net < 0 ? "−" : "+"} {euros(Math.abs(groupe.net))}
                  </span>
                </h3>
                <ul className="liste-mouvements">
                  {groupe.mouvements.map((m, i) => (
                    <li key={i} className="mouvement mouvement-jour">
                      <span
                        className={
                          "mouvement-barre " +
                          (m.type === "DEBIT" ? "barre-debit" : "barre-credit")
                        }
                      />
                      <span className="mouvement-libelle">
                        {m.libelle}
                        {m.origine === "TRANSACTION" && (
                          <em className="badge">pas encore debitee</em>
                        )}
                      </span>
                      <span
                        className={
                          "mouvement-montant " +
                          (m.type === "DEBIT" ? "texte-debit" : "texte-credit")
                        }
                      >
                        {m.type === "DEBIT" ? "−" : "+"} {euros(m.montant)}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </section>
    </TirerPourRafraichir>
  );
}
