import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { api, euros, formatLocal } from "./api";
import { abonnementActuel } from "./push";
import Connexion from "./pages/Connexion";
import Tableau from "./pages/Tableau";
import Calendrier from "./pages/Calendrier";
import Operations from "./pages/Operations";
import Transactions from "./pages/Transactions";
import Import from "./pages/Import";
import Rapprochement from "./pages/Rapprochement";
import Banque from "./pages/Banque";
import CallbackBanque from "./pages/CallbackBanque";
import Parametres from "./pages/Parametres";
import Regles from "./pages/Regles";
import Scenarios from "./pages/Scenarios";

/**
 * A l ouverture (une fois par jour max), notification navigateur avec les
 * mouvements du jour. Pas de spam : la date est memorisee en localStorage.
 */
async function notifierAujourdhui() {
  if (typeof Notification === "undefined") return;
  if (Notification.permission !== "granted") return;
  // Si l appareil est abonne au push, le serveur s en charge deja :
  // pas la peine d en remettre une couche a l ouverture.
  if (await abonnementActuel()) return;
  const cle = "tlprice-notif-jour";
  const aujourdhui = formatLocal(new Date());
  if (localStorage.getItem(cle) === aujourdhui) return;
  try {
    const projection = await api.get("/api/solde/projete?date=" + aujourdhui);
    const jour = projection.mouvements.filter((m: any) => m.date === aujourdhui);
    if (jour.length === 0) return;
    let debits = 0, credits = 0;
    for (const m of jour) {
      if (m.type === "DEBIT") debits += Number(m.montant);
      else credits += Number(m.montant);
    }
    const parts: string[] = [];
    if (debits > 0) parts.push(jour.filter((m: any) => m.type === "DEBIT").length + " prelevement(s) pour " + euros(debits));
    if (credits > 0) parts.push(jour.filter((m: any) => m.type === "CREDIT").length + " credit(s) pour " + euros(credits));
    new Notification("tlprice · aujourd hui", {
      body: parts.join(" · "),
      icon: "/icone-192.png",
    });
    localStorage.setItem(cle, aujourdhui);
  } catch {
    /* silencieux */
  }
}

const Icone = ({ d }: { d: string }) => (
  <svg
    className="nav-icone"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);

/**
 * `principal` marque les onglets de la barre du bas sur mobile. Quatre au
 * maximum : au-dela, les cibles deviennent trop etroites pour le pouce sur
 * un ecran de 393 px. Le reste passe dans la feuille "Plus".
 */
const ONGLETS = [
  // L ordre est celui de la barre du bas, de gauche a droite. Solde est au
  // centre : c est l ecran ouvert le plus souvent, et le centre est le point
  // le plus facile a atteindre au pouce sur un grand telephone.
  { chemin: "/calendrier", libelle: "Calendrier", principal: true, icone: "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4" },
  { chemin: "/operations", libelle: "Recurrent", principal: true, icone: "M4 10a8 8 0 0113-5m3 9a8 8 0 01-13 5M4 5v5h5m11 9v-5h-5" },
  { chemin: "/", libelle: "Solde", principal: true, centre: true, icone: "M3 17l5-6 4 4 6-8 3 3" },
  { chemin: "/transactions", libelle: "Ponctuel", principal: true, icone: "M12 5v14M5 12h14" },

  // Feuille "Plus", dans l ordre d ouverture reelle.
  { chemin: "/controle", libelle: "Controle", principal: false, icone: "M4 12l5 5L20 6" },
  { chemin: "/import", libelle: "Import", principal: false, icone: "M12 4v11m0 0l-4-4m4 4l4-4M5 19h14" },
  { chemin: "/parametres", libelle: "Reglages", principal: false, icone: "M12 9a3 3 0 100 6 3 3 0 000-6M4 12h2m12 0h2M12 4v2m0 12v2M6.3 6.3l1.4 1.4m8.6 8.6l1.4 1.4M17.7 6.3l-1.4 1.4M7.7 16.3l-1.4 1.4" },
  { chemin: "/scenarios", libelle: "Scenarios", principal: false, icone: "M4 18l5-7 4 3 6-9" },
  { chemin: "/regles", libelle: "Regles", principal: false, icone: "M4 6h16M4 12h10M4 18h7" },
  { chemin: "/banque", libelle: "Banque", principal: false, icone: "M3 10l9-6 9 6M5 10v9m14-9v9M3 19h18M9 10v9m6-9v9" },
];

const PRINCIPAUX = ONGLETS.filter((o) => o.principal);
const SECONDAIRES = ONGLETS.filter((o) => !o.principal);

export default function App() {
  const [email, setEmail] = useState<string | null>(null);
  const [chargement, setChargement] = useState(true);
  const [ecarts, setEcarts] = useState(0);
  const [feuille, setFeuille] = useState(false);
  const location = useLocation();

  useEffect(() => {
    api
      .get("/api/auth/moi")
      .then((moi) => setEmail(moi.email))
      .catch(() => setEmail(null))
      .finally(() => setChargement(false));
  }, []);

  useEffect(() => {
    if (email) notifierAujourdhui();
  }, [email]);

  // La pastille suit la navigation : traiter un ecart la met a jour.
  useEffect(() => {
    if (!email) return;
    api
      .get("/api/rapprochement/en-attente")
      .then((r) => setEcarts(r.nombre))
      .catch(() => {});
  }, [email, location.pathname]);

  // Fermer la feuille au changement de page, et empecher le fond de defiler
  // pendant qu elle est ouverte.
  useEffect(() => setFeuille(false), [location.pathname]);
  useEffect(() => {
    document.body.style.overflow = feuille ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [feuille]);

  if (chargement) return <div className="ecran-attente">tlprice</div>;
  if (!email) return <Connexion onConnecte={(e) => setEmail(e)} />;

  const deconnecter = async () => {
    await api.post("/api/auth/logout");
    setEmail(null);
  };

  const lien = (o: (typeof ONGLETS)[number], classe: string) => {
    const actif = location.pathname === o.chemin;
    const badge = o.chemin === "/controle" && ecarts > 0;
    return (
      <Link
        key={o.chemin}
        to={o.chemin}
        className={classe + (actif ? " actif" : "") + ("centre" in o && o.centre ? " nav-centre" : "")}
        aria-current={actif ? "page" : undefined}
      >
        <span className="nav-icone-boite">
          <Icone d={o.icone} />
          {badge && (
            <span className="nav-pastille" aria-label={ecarts + " a verifier"}>
              {ecarts > 9 ? "9+" : ecarts}
            </span>
          )}
        </span>
        <span className="nav-libelle">{o.libelle}</span>
      </Link>
    );
  };

  return (
    <div className="app">
      <a className="lien-evitement" href="#contenu">
        Aller au contenu
      </a>

      <header className="entete">
        <span className="marque">
          tlprice<span className="marque-point">·</span>
        </span>
        <nav className="nav nav-bureau" aria-label="Navigation principale">
          {ONGLETS.map((o) => lien(o, "nav-lien"))}
        </nav>
        <button className="bouton-texte deconnexion-bureau" onClick={deconnecter} title={email}>
          Se deconnecter
        </button>
      </header>

      <main className="contenu" id="contenu">
        <Routes>
          <Route path="/" element={<Tableau />} />
          <Route path="/calendrier" element={<Calendrier />} />
          <Route path="/operations" element={<Operations />} />
          <Route path="/transactions" element={<Transactions />} />
          <Route path="/import" element={<Import />} />
          <Route path="/controle" element={<Rapprochement />} />
          <Route path="/banque" element={<Banque />} />
          <Route path="/banque/callback" element={<CallbackBanque />} />
          <Route path="/parametres" element={<Parametres />} />
          <Route path="/scenarios" element={<Scenarios />} />
          <Route path="/regles" element={<Regles />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      {/* ---- Barre du bas, mobile uniquement ---- */}
      <nav className="nav-mobile" aria-label="Navigation">
        {PRINCIPAUX.map((o) => lien(o, "nav-lien"))}
        <button
          className={"nav-lien" + (feuille ? " actif" : "")}
          onClick={() => setFeuille((v) => !v)}
          aria-expanded={feuille}
          aria-label="Plus d options"
        >
          <span className="nav-icone-boite">
            <Icone d="M5 12h.01M12 12h.01M19 12h.01" />
          </span>
          <span className="nav-libelle">Plus</span>
        </button>
      </nav>

      {feuille && (
        <>
          <div className="feuille-voile" onClick={() => setFeuille(false)} />
          <div className="feuille" role="dialog" aria-label="Plus d options">
            <span className="feuille-poignee" aria-hidden="true" />
            <div className="feuille-grille">
              {SECONDAIRES.map((o) => (
                <Link key={o.chemin} to={o.chemin} className="feuille-item">
                  <Icone d={o.icone} />
                  <span>{o.libelle}</span>
                </Link>
              ))}
            </div>
            <button className="feuille-deconnexion" onClick={deconnecter}>
              Se deconnecter <em>{email}</em>
            </button>
          </div>
        </>
      )}
    </div>
  );
}
