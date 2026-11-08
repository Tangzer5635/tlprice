import { formatLocal } from "./api";

/** Un mouvement, reel ou hypothetique. */
export type MouvementSimple = {
  date: string;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
};

/**
 * Une ligne telle qu on l edite : une depense ou une rentree, ponctuelle ou
 * repetee chaque mois (credit, abonnement...). `repetition` = 1 pour une
 * ligne unique.
 */
export type LigneEdition = {
  cle: string;
  libelle: string;
  montant: string;
  type: "DEBIT" | "CREDIT";
  categorie: string;
  date: string;
  repetition: number;
};

/** Une ligne telle que l API la stocke. */
export type LigneApi = {
  id?: number;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
  categorie: string;
  date: string;
};

export type Point = { date: string; reel: number; avec: number };

export type Verdict = {
  niveau: "ok" | "seuil" | "decouvert";
  /** Premiere date ou le probleme apparait (null si ok). */
  date: string | null;
  pointBas: number;
  datePointBas: string;
  finReel: number;
  finAvec: number;
  /** Marge restante au point bas, au-dessus du seuil (ou de zero). */
  marge: number;
  /** Le probleme existe-t-il deja sans le scenario ? */
  dejaSansScenario: boolean;
};

let compteur = 0;
export const nouvelleCle = () => "l" + ++compteur;

export const ajouterMois = (iso: string, n: number): string => {
  const d = new Date(iso + "T00:00:00");
  const jour = d.getDate();
  const cible = new Date(d.getFullYear(), d.getMonth() + n, 1);
  const dernier = new Date(cible.getFullYear(), cible.getMonth() + 1, 0).getDate();
  cible.setDate(Math.min(jour, dernier));
  return formatLocal(cible);
};

const jourSuivant = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + 1);
  return formatLocal(d);
};

export const montantNombre = (m: string) => Number(String(m).replace(",", ".").replace(/\s/g, ""));

/** Lignes d edition -> mouvements dates (les mensualites sont depliees). */
export function deplier(lignes: LigneEdition[]): LigneApi[] {
  const sortie: LigneApi[] = [];
  for (const l of lignes) {
    const montant = montantNombre(l.montant);
    if (!l.libelle.trim() || !(montant > 0) || !l.date) continue;
    const n = Math.max(1, l.repetition);
    for (let k = 0; k < n; k++) {
      sortie.push({
        libelle: n > 1 ? `${l.libelle.trim()} (${k + 1}/${n})` : l.libelle.trim(),
        montant,
        type: l.type,
        categorie: l.categorie,
        date: ajouterMois(l.date, k),
      });
    }
  }
  return sortie;
}

/** Inverse de deplier : regroupe « Credit (1/12) ... (12/12) » en une ligne. */
export function replier(lignes: LigneApi[]): LigneEdition[] {
  const groupes = new Map<string, { base: LigneApi; total: number; dates: string[] }>();
  const seules: LigneEdition[] = [];
  for (const l of lignes) {
    const m = l.libelle.match(/^(.*) \((\d+)\/(\d+)\)$/);
    if (!m) {
      seules.push({
        cle: nouvelleCle(),
        libelle: l.libelle,
        montant: String(l.montant),
        type: l.type,
        categorie: l.categorie,
        date: l.date,
        repetition: 1,
      });
      continue;
    }
    const k = [m[1], l.montant, l.type, m[3]].join("|");
    const g = groupes.get(k);
    if (g) g.dates.push(l.date);
    else groupes.set(k, { base: { ...l, libelle: m[1] }, total: Number(m[3]), dates: [l.date] });
  }
  const repliees: LigneEdition[] = [];
  for (const g of groupes.values()) {
    g.dates.sort();
    repliees.push({
      cle: nouvelleCle(),
      libelle: g.base.libelle,
      montant: String(g.base.montant),
      type: g.base.type,
      categorie: g.base.categorie,
      date: g.dates[0],
      repetition: g.dates.length,
    });
  }
  return [...seules, ...repliees].sort((a, b) => a.date.localeCompare(b.date));
}

/**
 * Deux trajectoires jour par jour, sans et avec le scenario, comme le
 * calcule le serveur : le solde bancaire, puis tout ce qui tombe apres sa
 * date de mise a jour. Les lignes de scenario anterieures a cette date sont
 * ignorees (le serveur fait de meme).
 */
export function trajectoires(
  soldeReel: number,
  dateMaj: string,
  horizon: string,
  reels: MouvementSimple[],
  hypotheses: LigneApi[]
): Point[] {
  const effet = (m: { type: string; montant: number }) =>
    m.type === "CREDIT" ? Number(m.montant) : -Number(m.montant);

  const parJourReel = new Map<string, number>();
  let depart = Number(soldeReel);
  for (const m of reels) {
    // Achats pas encore debites, dates d avant la mise a jour : deja dus.
    if (m.date <= dateMaj) depart += effet(m);
    else parJourReel.set(m.date, (parJourReel.get(m.date) ?? 0) + effet(m));
  }
  const parJourHyp = new Map<string, number>();
  for (const h of hypotheses) {
    if (h.date <= dateMaj || h.date > horizon) continue;
    parJourHyp.set(h.date, (parJourHyp.get(h.date) ?? 0) + effet(h));
  }

  const points: Point[] = [{ date: dateMaj, reel: depart, avec: depart }];
  let reel = depart;
  let avec = depart;
  let jour = jourSuivant(dateMaj);
  for (let garde = 0; jour <= horizon && garde < 800; garde++) {
    const r = parJourReel.get(jour) ?? 0;
    reel += r;
    avec += r + (parJourHyp.get(jour) ?? 0);
    points.push({ date: jour, reel, avec });
    jour = jourSuivant(jour);
  }
  return points;
}

export function verdict(points: Point[], seuil: number | null): Verdict {
  const aujourdhui = formatLocal(new Date());
  const futurs = points.filter((p) => p.date >= aujourdhui);
  const serie = futurs.length > 0 ? futurs : points.slice(-1);
  const limite = seuil ?? 0;

  let bas = serie[0];
  let premierDecouvert: string | null = null;
  let premierSeuil: string | null = null;
  let reelDecouvert = false;
  let reelSeuil = false;
  for (const p of serie) {
    if (p.avec < bas.avec) bas = p;
    if (p.avec < 0 && !premierDecouvert) premierDecouvert = p.date;
    if (seuil != null && p.avec < seuil && !premierSeuil) premierSeuil = p.date;
    if (p.reel < 0) reelDecouvert = true;
    if (seuil != null && p.reel < seuil) reelSeuil = true;
  }
  const fin = serie[serie.length - 1];
  const niveau = premierDecouvert ? "decouvert" : premierSeuil ? "seuil" : "ok";
  return {
    niveau,
    date: premierDecouvert ?? premierSeuil,
    pointBas: bas.avec,
    datePointBas: bas.date,
    finReel: fin.reel,
    finAvec: fin.avec,
    marge: bas.avec - limite,
    dejaSansScenario: niveau === "decouvert" ? reelDecouvert : niveau === "seuil" ? reelSeuil : false,
  };
}
