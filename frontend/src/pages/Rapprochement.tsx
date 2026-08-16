import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, dateFr, euros } from "../api";

type Statut = "CONFIRME" | "ECART" | "MANQUANT";
type Confiance = "CERTAIN" | "PROBABLE" | "AUCUNE";

type Ligne = {
  id: number;
  operationId: number;
  libelle: string;
  dateEcheance: string;
  dateBancaire: string | null;
  montantPrevu: number;
  montantReel: number | null;
  ecart: number | null;
  libelleBancaire: string | null;
  statut: Statut;
  confiance: Confiance;
  traite: boolean;
};

export default function Rapprochement() {
  const [lignes, setLignes] = useState<Ligne[]>([]);
  const [tout, setTout] = useState(false);
  const [analyse, setAnalyse] = useState(false);
  const [chargement, setChargement] = useState(true);
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");
  const [regleApprise, setRegleApprise] = useState<string | null>(null);

  const charger = useCallback(async (afficherTout: boolean) => {
    setChargement(true);
    try {
      const r = await api.get("/api/rapprochement?tout=" + afficherTout);
      setLignes(r);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setChargement(false);
    }
  }, []);

  useEffect(() => {
    charger(tout);
  }, [tout, charger]);

  const relancer = async () => {
    setAnalyse(true);
    setErreur("");
    setMessage("");
    setRegleApprise(null);
    try {
      const r = await api.post("/api/rapprochement/analyser?jours=45");
      setTout(false);
      setLignes(r);
      setMessage(
        r.length === 0
          ? "Tout est conforme sur les 45 derniers jours."
          : r.length + (r.length > 1 ? " points a verifier." : " point a verifier.")
      );
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setAnalyse(false);
    }
  };

  const agir = async (id: number, action: string, retour: string) => {
    setErreur("");
    setRegleApprise(null);
    try {
      const reponse = await api.post("/api/rapprochement/" + id + "/" + action);
      setMessage(retour);
      // Le serveur a peut-etre appris une regle de renommage au passage.
      if (reponse && reponse.regleApprise) setRegleApprise(reponse.regleApprise);
      await charger(tout);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  return (
    <div>
      <h1 className="titre-page">Controle</h1>
      <p className="intro">
        Ce que tlprice annoncait, face a ce que ta banque a reellement
        debite. Les ecarts silencieux — un abonnement qui augmente de deux
        euros, un loyer revalorise — sont ce qui fait deriver une projection
        sans qu on s en apercoive.
      </p>

      <div className="controle-barre">
        <button className="bouton" onClick={relancer} disabled={analyse}>
          {analyse ? "Analyse en cours…" : "Analyser les 45 derniers jours"}
        </button>
        <label className="ligne-interrupteur" style={{ marginTop: 0 }}>
          <input
            type="checkbox"
            checked={tout}
            onChange={(e) => setTout(e.target.checked)}
          />
          <span>Afficher aussi ce qui est conforme ou deja traite</span>
        </label>
      </div>

      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}

      {regleApprise && (
        <div className="bandeau-appris">
          <strong>Regle apprise : « {regleApprise} »</strong>
          <span>
            Cette ligne bancaire sera reconnue toute seule les prochains mois.
            Si l appariement etait faux, <Link to="/regles">retire la regle</Link>.
          </span>
        </div>
      )}

      {chargement ? (
        <p className="attente">Chargement…</p>
      ) : lignes.length === 0 ? (
        <p className="vide">
          {tout
            ? "Aucune analyse enregistree. Lance l analyse pour confronter tes echeances a ta banque."
            : "Rien a verifier. Tes echeances correspondent a tes debits."}
        </p>
      ) : (
        <ul className="liste-cartes">
          {lignes.map((l) => (
            <li key={l.id} className={"carte-controle statut-" + l.statut.toLowerCase()}>
              <div className="controle-tete">
                <span className="controle-libelle">{l.libelle}</span>
                <span className={"etiquette-statut s-" + l.statut.toLowerCase()}>
                  {l.statut === "CONFIRME"
                    ? "Conforme"
                    : l.statut === "ECART"
                      ? "Ecart de montant"
                      : "Non debite"}
                </span>
                {l.traite && <span className="etiquette-statut s-traite">Traite</span>}
              </div>

              {/* Prevu / reel cote a cote : c est la comparaison qui compte,
                  et deux nombres alignes se comparent plus vite que deux
                  phrases. */}
              <div className="controle-duo">
                <span className="duo-bloc">
                  <em>Prevu le {dateFr(l.dateEcheance)}</em>
                  <strong>{euros(l.montantPrevu)}</strong>
                </span>
                <span className="duo-bloc">
                  <em>
                    {l.montantReel !== null
                      ? "Debite" + (l.dateBancaire ? " le " + dateFr(l.dateBancaire) : "")
                      : "En banque"}
                  </em>
                  {l.montantReel !== null ? (
                    <strong>{euros(l.montantReel)}</strong>
                  ) : (
                    <strong className="texte-debit">Rien trouve</strong>
                  )}
                </span>
                {l.ecart !== null && Math.abs(l.ecart) > 0.005 && (
                  <span className="duo-bloc">
                    <em>Ecart</em>
                    <strong className={l.ecart > 0 ? "texte-debit" : "texte-credit"}>
                      {l.ecart > 0 ? "+" : "−"} {euros(Math.abs(l.ecart))}
                    </strong>
                  </span>
                )}
              </div>

              {l.libelleBancaire && (
                <p className="controle-source">
                  Ligne bancaire : <em>{l.libelleBancaire}</em>
                  {l.confiance === "PROBABLE" && (
                    <span className="badge">
                      rapprochee sur le montant, verifie que c est la bonne
                    </span>
                  )}
                </p>
              )}

              {!l.traite && (
                <div className="controle-actions">
                  {l.statut === "ECART" && (
                    <>
                      <button
                        className="bouton"
                        onClick={() =>
                          agir(
                            l.id,
                            "definitif",
                            "Montant de l operation mis a jour pour les prochaines echeances."
                          )
                        }
                      >
                        C est le nouveau montant
                      </button>
                      <button
                        className="bouton secondaire"
                        onClick={() =>
                          agir(l.id, "ponctuel", "Ajustement enregistre pour cette date seulement.")
                        }
                      >
                        Juste cette fois
                      </button>
                    </>
                  )}
                  {l.statut === "MANQUANT" && (
                    <button
                      className="bouton"
                      onClick={() =>
                        agir(l.id, "annuler-echeance", "Echeance annulee pour cette date.")
                      }
                    >
                      Ce prelevement n a pas eu lieu
                    </button>
                  )}
                  <button
                    className="bouton-texte"
                    onClick={() => agir(l.id, "ignorer", "Ligne classee.")}
                  >
                    Ignorer
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      <p className="note">
        Depuis la v3, confirmer une correction sur une ligne rapprochee « sur
        le montant » cree automatiquement la regle de renommage
        correspondante : le mois suivant, la correspondance est immediate.
        Les regles apprises se consultent et se retirent dans{" "}
        <Link to="/regles">Regles de renommage</Link>.
      </p>
    </div>
  );
}
