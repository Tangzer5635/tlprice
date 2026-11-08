// Petit client HTTP : meme origine, cookies de session + jeton CSRF.

function lireCookie(nom: string): string | null {
  const m = document.cookie.match(new RegExp("(?:^|; )" + nom + "=([^;]*)"));
  return m ? decodeURIComponent(m[1]) : null;
}

/**
 * Evenements globaux, ecoutes par App :
 *  - "tlprice:session-expiree" : une requete authentifiee a recu 401. Le
 *    cookie de connexion a expire (ou le mot de passe a change ailleurs) :
 *    on revient a l ecran de connexion au lieu d afficher « Erreur 401 »
 *    sur chaque bloc.
 *  - "tlprice:hors-ligne" : la reponse vient du cache du service worker,
 *    le serveur est injoignable. detail = date de la donnee.
 *  - "tlprice:en-ligne" : une vraie reponse du serveur est arrivee.
 */
export const EVT_SESSION_EXPIREE = "tlprice:session-expiree";
export const EVT_HORS_LIGNE = "tlprice:hors-ligne";
export const EVT_EN_LIGNE = "tlprice:en-ligne";

const signaler = (nom: string, detail?: unknown) =>
  window.dispatchEvent(new CustomEvent(nom, { detail }));

/** Les routes d auth gerent leur 401 elles-memes (mauvais mot de passe...). */
const routeAuth = (url: string) => url.startsWith("/api/auth/");

export class ErreurApi extends Error {
  statut: number;
  constructor(statut: number, message: string) {
    super(message);
    this.statut = statut;
  }
}

async function requete(methode: string, url: string, corps?: unknown, brut?: BodyInit) {
  const entetes: Record<string, string> = {};
  if (methode !== "GET") {
    const jeton = lireCookie("XSRF-TOKEN");
    if (jeton) entetes["X-XSRF-TOKEN"] = jeton;
  }
  if (corps !== undefined) entetes["Content-Type"] = "application/json";

  let reponse: Response;
  try {
    reponse = await fetch(url, {
      method: methode,
      headers: entetes,
      body: corps !== undefined ? JSON.stringify(corps) : brut,
      credentials: "same-origin",
    });
  } catch {
    signaler(EVT_HORS_LIGNE, null);
    throw new ErreurApi(0, "Serveur injoignable. Verifie ta connexion.");
  }

  const horsLigne = reponse.headers.get("X-Tlprice-Hors-Ligne");
  if (horsLigne) signaler(EVT_HORS_LIGNE, horsLigne);
  else if (reponse.status < 500) signaler(EVT_EN_LIGNE);

  if (reponse.status === 401 && !routeAuth(url)) signaler(EVT_SESSION_EXPIREE);

  if (reponse.status >= 500 && !horsLigne) {
    // Cloudflare renvoie une page HTML quand le Pi ne repond pas.
    const typeContenu = reponse.headers.get("Content-Type") || "";
    if (!typeContenu.includes("json")) {
      signaler(EVT_HORS_LIGNE, null);
      throw new ErreurApi(reponse.status, "Serveur injoignable pour le moment.");
    }
  }

  if (!reponse.ok) {
    let message = "Erreur " + reponse.status;
    try {
      const json = await reponse.json();
      if (json.message) message = json.message;
    } catch {
      /* pas de corps JSON */
    }
    throw new ErreurApi(reponse.status, message);
  }
  const texte = await reponse.text();
  return texte ? JSON.parse(texte) : null;
}

export const api = {
  get: (url: string) => requete("GET", url),
  post: (url: string, corps?: unknown) => requete("POST", url, corps),
  put: (url: string, corps: unknown) => requete("PUT", url, corps),
  patch: (url: string, corps?: unknown) => requete("PATCH", url, corps),
  /** Un DELETE peut porter un corps (desabonnement push). */
  supprimer: (url: string, corps?: unknown) => requete("DELETE", url, corps),
  /** POST multipart (upload de la cle .pem) */
  postFormulaire: (url: string, donnees: FormData) =>
    requete("POST", url, undefined, donnees),
  /** POST x-www-form-urlencoded (login Spring Security) */
  postLogin: (email: string, motDePasse: string) => {
    const params = new URLSearchParams();
    params.set("username", email);
    params.set("password", motDePasse);
    return requete("POST", "/api/auth/login", undefined, params);
  },
};

/**
 * Telecharge un fichier renvoye par l API (export CSV / Excel).
 * On passe par un blob plutot qu un lien direct pour pouvoir remonter
 * proprement une erreur au lieu d ouvrir un onglet avec du JSON.
 */
export async function telecharger(url: string, nomParDefaut: string) {
  const reponse = await fetch(url, { credentials: "same-origin" });
  if (!reponse.ok) {
    let message = "Erreur " + reponse.status;
    try {
      const json = await reponse.json();
      if (json.message) message = json.message;
    } catch {
      /* pas de corps JSON */
    }
    throw new ErreurApi(reponse.status, message);
  }

  const entete = reponse.headers.get("Content-Disposition") || "";
  const trouve = entete.match(/filename="?([^"]+)"?/);
  const nom = trouve ? trouve[1] : nomParDefaut;

  const blob = await reponse.blob();
  const adresse = URL.createObjectURL(blob);
  const lien = document.createElement("a");
  lien.href = adresse;
  lien.download = nom;
  document.body.appendChild(lien);
  lien.click();
  document.body.removeChild(lien);
  URL.revokeObjectURL(adresse);
}

export const euros = (montant: number | string) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    Number(montant)
  );

export const dateFr = (iso: string) =>
  new Date(iso + "T00:00:00").toLocaleDateString("fr-FR", {
    day: "numeric",
    month: "short",
  });

export type Mouvement = {
  date: string;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
  categorie: string;
  origine: "ECHEANCE" | "TRANSACTION";
  operationId: number | null;
  montantOriginal: number | null;
};

export type JourCalendrier = {
  date: string;
  mouvements: Mouvement[];
  soldeProjete: number;
};

export type TransactionBancaire = {
  reference: string | null;
  date: string;
  libelle: string;
  libelleAuto: string | null;
  categorieAuto: string | null;
  dejaImporte: boolean;
  montant: number;
  type: "DEBIT" | "CREDIT";
};

export type RegleRenommage = {
  id: number;
  motif: string;
  libelleFinal: string;
  categorie: string;
};

/** Une sauvegarde nocturne presente sur le serveur. */
export type FichierSauvegarde = {
  nom: string;
  date: string;
  taille: number;
};

export type ConfigSmtp = {
  host: string | null;
  port: number;
  username: string | null;
  expediteur: string | null;
  starttls: boolean;
  configuree: boolean;
};

export type ParametresApp = {
  id?: number;
  email: string | null;
  alertesEmail: boolean;
  seuilAlerte: number | null;
  /** Heure du recap EMAIL. */
  heureRecap: number;
  pushActif: boolean;
  /** Heure du push, independante de celle de l email. */
  heureRecapPush: number;
  pushSiVide: boolean;
};

export type AlerteSolde = {
  seuil: number | null;
  soldeMinimum: number | null;
  dateMinimum: string | null;
  alerte: boolean;
};

/**
 * Renvoie une date au format YYYY-MM-DD dans le fuseau LOCAL.
 * A utiliser partout au lieu de toISOString().slice(0, 10), qui convertit
 * en UTC et decale la date d un jour selon l heure et le fuseau.
 */
export const formatLocal = (d: Date): string => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const j = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${j}`;
};

export const CATEGORIES = [
  "LOGEMENT", "ALIMENTATION", "TRANSPORT", "ABONNEMENTS",
  "SANTE", "LOISIRS", "REVENUS", "EPARGNE", "AUTRE",
] as const;

export const LIBELLES_CATEGORIES: Record<string, string> = {
  LOGEMENT: "Logement", ALIMENTATION: "Alimentation", TRANSPORT: "Transport",
  ABONNEMENTS: "Abonnements", SANTE: "Sante", LOISIRS: "Loisirs",
  REVENUS: "Revenus", EPARGNE: "Epargne", AUTRE: "Autre",
};

/**
 * Efface les donnees gardees hors ligne par le service worker. A appeler a
 * la deconnexion : sur un appareil partage, la personne suivante ne doit
 * pas pouvoir relire tes soldes en coupant le Wi-Fi.
 */
export async function viderDonneesLocales() {
  try {
    if ("caches" in window) await caches.delete("tlprice-api");
    navigator.serviceWorker?.controller?.postMessage({ type: "vider-donnees" });
  } catch {
    /* rien a vider */
  }
}
