import { useEffect, useMemo, useRef, useState } from "react";
import { api, euros, JourCalendrier, Mouvement, formatLocal } from "../api";
import FeuilleBasse from "../components/FeuilleBasse";

type Mode = "civil" | "paie";

const NOMS_MOIS = [
  "janvier", "fevrier", "mars", "avril", "mai", "juin",
  "juillet", "aout", "septembre", "octobre", "novembre", "decembre",
];

const NOMS_JOURS = ["lun", "mar", "mer", "jeu", "ven", "sam", "dim"];

const isoMois = (annee: number, mois: number) =>
  `${annee}/${mois.toString().padStart(2, "0")}`;

/** Au-dela, le mouvement horizontal est un changement de mois, pas un tap. */
const SEUIL_BALAYAGE = 60;

export default function Calendrier() {
  const [aujourdhui] = useState(new Date());
  const [annee, setAnnee] = useState(aujourdhui.getFullYear());
  const [mois, setMois] = useState(aujourdhui.getMonth() + 1);
  // Jours du mois affiche
  const [jours, setJours] = useState<JourCalendrier[]>([]);
  // Jours du mois suivant (necessaires pour le calcul du cycle de paie)
  const [joursSuivants, setJoursSuivants] = useState<JourCalendrier[]>([]);
  const [mode, setMode] = useState<Mode>("paie");
  const [jourAgenda, setJourAgenda] = useState<JourCalendrier | null>(null);
  const [erreur, setErreur] = useState("");
  const [edition, setEdition] = useState<{ operationId: number; date: string } | null>(null);
  const [montantEdition, setMontantEdition] = useState("");

  const charger = () => {
    api.get(`/api/calendrier/${isoMois(annee, mois)}`)
      .then(setJours)
      .catch((e) => setErreur(e.message));
    // Mois suivant, pour le cycle de paie
    const ms = mois === 12 ? 1 : mois + 1;
    const as = mois === 12 ? annee + 1 : annee;
    api.get(`/api/calendrier/${isoMois(as, ms)}`)
      .then(setJoursSuivants)
      .catch(() => setJoursSuivants([]));
  };

  useEffect(charger, [annee, mois]);

  // ---- Bandeau recap ----
  const recap = useMemo(() => {
    let debut: string, fin: string;
    if (mode === "civil") {
      if (jours.length === 0) return { revenus: 0, depenses: 0 };
      debut = jours[0].date;
      fin = jours[jours.length - 1].date;
    } else {
      const ms = mois === 12 ? 1 : mois + 1;
      const as = mois === 12 ? annee + 1 : annee;
      debut = `${annee}-${String(mois).padStart(2, "0")}-25`;
      fin = `${as}-${String(ms).padStart(2, "0")}-24`;
    }
    const tousJours = mode === "paie" ? [...jours, ...joursSuivants] : jours;
    let revenus = 0, depenses = 0;
    for (const j of tousJours) {
      if (j.date < debut || j.date > fin) continue;
      for (const m of j.mouvements) {
        if (m.origine !== "ECHEANCE") continue;
        if (m.type === "CREDIT") revenus += Number(m.montant);
        else depenses += Number(m.montant);
      }
    }
    return { revenus, depenses };
  }, [jours, joursSuivants, mode, mois, annee]);

  const naviguer = (delta: number) => {
    let m = mois + delta;
    let a = annee;
    if (m > 12) { m = 1; a++; }
    if (m < 1) { m = 12; a--; }
    setMois(m);
    setAnnee(a);
  };

  // ---- Balayage horizontal pour changer de mois ----
  // Les fleches restent, mais viser un chevron de 30 px au pouce alors que la
  // grille occupe tout l ecran, c est demander un effort pour l action la
  // plus frequente de cet ecran.
  const geste = useRef({ x: 0, y: 0, balaye: false });

  const debutGeste = (e: React.TouchEvent) => {
    geste.current = { x: e.touches[0].clientX, y: e.touches[0].clientY, balaye: false };
  };

  const finGeste = (e: React.TouchEvent) => {
    const dx = e.changedTouches[0].clientX - geste.current.x;
    const dy = e.changedTouches[0].clientY - geste.current.y;
    // Un mouvement plus vertical qu horizontal est un defilement.
    if (Math.abs(dx) < SEUIL_BALAYAGE || Math.abs(dy) > Math.abs(dx)) return;
    geste.current.balaye = true;
    naviguer(dx < 0 ? 1 : -1);
  };

  const decalage = useMemo(() => {
    if (jours.length === 0) return 0;
    const premier = new Date(jours[0].date + "T00:00:00");
    return (premier.getDay() + 6) % 7;
  }, [jours]);

  // ---- Ajustement d occurrence ----
  const commencerEdition = (m: Mouvement, date: string) => {
    if (!m.operationId) return;
    setEdition({ operationId: m.operationId, date });
    setMontantEdition(String(m.montant));
  };

  const validerAjustement = async () => {
    if (!edition) return;
    const brut = montantEdition.replace(",", ".").trim();
    if (brut === "") return;
    try {
      await api.post(`/api/operations/${edition.operationId}/exceptions`, {
        date: edition.date,
        montantOverride: Number(brut),
        annulee: false,
      });
      setEdition(null);
      charger();
      // Rafraichir le contenu de la fenetre agenda
      setJourAgenda(null);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const annulerOccurrence = async (m: Mouvement, date: string) => {
    if (!m.operationId) return;
    try {
      await api.post(`/api/operations/${m.operationId}/exceptions`, {
        date,
        annulee: true,
      });
      charger();
      setJourAgenda(null);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const reinitialiserOccurrence = async (m: Mouvement, date: string) => {
    if (!m.operationId) return;
    try {
      await api.supprimer(`/api/operations/${m.operationId}/exceptions?date=${date}`);
      charger();
      setJourAgenda(null);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const auJourdhuiISO = formatLocal(aujourdhui);
  const reste = recap.revenus - recap.depenses;

  return (
    <div>
      <div className="titre-mois">
        <button className="fleche" onClick={() => naviguer(-1)} aria-label="Mois precedent">‹</button>
        <h1 className="titre-page nomargin">{NOMS_MOIS[mois - 1]} {annee}</h1>
        <button className="fleche" onClick={() => naviguer(1)} aria-label="Mois suivant">›</button>
      </div>

      <section className="recap-mois">
        <div className="recap-bloc">
          <span className="etiquette">Revenus</span>
          <div className="recap-valeur texte-credit">+ {euros(recap.revenus)}</div>
        </div>
        <div className="recap-bloc">
          <span className="etiquette">Depenses</span>
          <div className="recap-valeur texte-debit">− {euros(recap.depenses)}</div>
        </div>
        <div className="recap-bloc">
          <span className="etiquette">Reste</span>
          <div className={"recap-valeur" + (reste < 0 ? " texte-debit" : "")}>{euros(reste)}</div>
        </div>
        <div className="cycle-choix">
          <div className="bascule">
            <button className={mode === "civil" ? "actif" : ""} onClick={() => setMode("civil")}>Mois civil</button>
            <button className={mode === "paie" ? "actif" : ""} onClick={() => setMode("paie")}>Cycle de paie</button>
          </div>
          <p className="note">
            {mode === "paie" ? "Du 25 au 24 du mois suivant." : "Du 1er au dernier jour du mois."}
          </p>
        </div>
      </section>

      {erreur && <p className="message-erreur">{erreur}</p>}

      <div
        className="calendrier"
        onTouchStart={debutGeste}
        onTouchEnd={finGeste}
      >
        {NOMS_JOURS.map((j) => <div key={j} className="entete-jour">{j}</div>)}
        {Array.from({ length: decalage }).map((_, i) => (
          <div key={"vide-" + i} className="cellule vide" />
        ))}
        {jours.map((jour) => {
          const numero = jour.date.slice(-2);
          const estAujourdhui = jour.date === auJourdhuiISO;
          const negatif = jour.soldeProjete < 0;
          return (
            <button
              key={jour.date}
              className={"cellule" + (estAujourdhui ? " aujourdhui" : "") + (negatif ? " solde-negatif" : "")}
              onClick={() => {
                // Un balayage traverse plusieurs cellules et finirait par
                // ouvrir celle sous le doigt : on l ignore.
                if (geste.current.balaye) {
                  geste.current.balaye = false;
                  return;
                }
                setJourAgenda(jour);
              }}
            >
              <span className="numero">{numero}</span>

              {/* Sur telephone, une cellule fait 50 px de large : un libelle
                  y est illisible. On montre des points colores, qui disent
                  la seule chose utile a cette taille — combien de mouvements
                  et dans quel sens. Les libelles restent sur grand ecran. */}
              <span className="points-jour" aria-hidden="true">
                {jour.mouvements.slice(0, 4).map((m, i) => (
                  <i key={i} className={"point " + (m.type === "DEBIT" ? "debit" : "credit")} />
                ))}
              </span>

              {jour.mouvements.slice(0, 3).map((m, i) => (
                <span
                  key={i}
                  className={"pastille " + (m.type === "DEBIT" ? "debit" : "credit")}
                  title={m.libelle + " · " + euros(m.montant)}
                >
                  {m.libelle}
                </span>
              ))}
              {jour.mouvements.length > 3 && (
                <span className="pastille-plus">+{jour.mouvements.length - 3}</span>
              )}
              <span className={"solde-jour" + (negatif ? " negatif" : "")}>
                {euros(jour.soldeProjete)}
              </span>
            </button>
          );
        })}
      </div>

      <p className="note note-geste">Balaye la grille pour changer de mois.</p>

      {jourAgenda && (
        <FeuilleBasse
          onFermer={() => { setJourAgenda(null); setEdition(null); }}
          titre={
            <>
              <p className="hero-eyebrow">
                {new Date(jourAgenda.date + "T00:00:00").toLocaleDateString("fr-FR",
                  { weekday: "long", day: "numeric", month: "long" })}
              </p>
              <p className="agenda-solde">
                Solde en fin de journee : <strong>{euros(jourAgenda.soldeProjete)}</strong>
              </p>
            </>
          }
        >
          {jourAgenda.mouvements.length === 0 ? (
            <p className="vide">Aucun mouvement ce jour.</p>
          ) : (
            <ul className="liste-mouvements">
              {jourAgenda.mouvements.map((m, i) => {
                const modif = m.montantOriginal !== null;
                const enEdition = edition
                  && m.operationId === edition.operationId
                  && jourAgenda.date === edition.date;
                return (
                  <li key={i} className="mouvement mouvement-modal">
                    <span className={"mouvement-barre " + (m.type === "DEBIT" ? "barre-debit" : "barre-credit")} />
                    <span className="mouvement-libelle">
                      {m.libelle}
                      {m.origine === "TRANSACTION" && <em className="badge">ponctuel</em>}
                      {modif && <em className="badge auto">modifie</em>}
                      {modif && (
                        <em className="montant-original">
                          (au lieu de {euros(m.montantOriginal!)})
                        </em>
                      )}
                    </span>
                    <span className={"mouvement-montant " + (m.type === "DEBIT" ? "texte-debit" : "texte-credit")}>
                      {m.type === "DEBIT" ? "−" : "+"} {euros(m.montant)}
                    </span>
                    {m.origine === "ECHEANCE" && m.operationId && (
                      <div className="actions-occurrence">
                        {enEdition ? (
                          <>
                            <input
                              inputMode="decimal"
                              value={montantEdition}
                              onChange={(e) => setMontantEdition(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === "Enter") validerAjustement();
                                if (e.key === "Escape") setEdition(null);
                              }}
                              autoFocus
                              className="input-montant"
                            />
                            <button className="bouton-texte" onClick={validerAjustement}>Enregistrer</button>
                            <button className="bouton-texte" onClick={() => setEdition(null)}>Annuler</button>
                          </>
                        ) : (
                          <>
                            <button className="bouton-texte" onClick={() => commencerEdition(m, jourAgenda.date)}>
                              Ajuster
                            </button>
                            <button className="bouton-texte" onClick={() => annulerOccurrence(m, jourAgenda.date)}>
                              Ne pas compter
                            </button>
                            {modif && (
                              <button className="bouton-texte" onClick={() => reinitialiserOccurrence(m, jourAgenda.date)}>
                                Reinitialiser
                              </button>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </FeuilleBasse>
      )}
    </div>
  );
}
