import { useMemo, useRef, useState } from "react";
import { dateFr, euros, formatLocal } from "../api";

type MouvementCourbe = {
  date: string;
  libelle: string;
  montant: number;
  type: "DEBIT" | "CREDIT";
};

export type PointPasse = { date: string; soldeReel: number };

type Props = {
  soldeReel: number;
  dateMaj: string;
  dateProjection: string;
  mouvements: MouvementCourbe[];
  seuil?: number | null;
  /** Soldes reellement constates les jours precedents. */
  historique?: PointPasse[];
};

type Point = {
  date: string;
  solde: number;
  mouvements: MouvementCourbe[];
  /** Vrai pour les points issus de l historique, faux pour la projection. */
  passe: boolean;
};

/* Geometrie du dessin : le SVG est en viewBox fixe et s etire en largeur. */
const L = 700;
const H = 200;
const MARGE_G = 8;
const MARGE_D = 8;
const MARGE_H = 18;
const MARGE_B = 22;

const jourSuivant = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + 1);
  return formatLocal(d);
};

/**
 * Serie jour par jour du solde projete, de la date de mise a jour du solde
 * bancaire jusqu a la date d horizon. Le solde d un jour est celui de la
 * FIN de journee : tous les mouvements du jour sont deja appliques.
 */
function construireSerie(
  soldeReel: number,
  dateMaj: string,
  dateProjection: string,
  mouvements: MouvementCourbe[]
): Point[] {
  const parJour = new Map<string, MouvementCourbe[]>();
  for (const m of mouvements) {
    const liste = parJour.get(m.date);
    if (liste) liste.push(m);
    else parJour.set(m.date, [m]);
  }

  const points: Point[] = [];
  let solde = Number(soldeReel);
  points.push({ date: dateMaj, solde, mouvements: [], passe: false });

  let curseur = jourSuivant(dateMaj);
  let garde = 0;
  while (curseur <= dateProjection && garde < 800) {
    const duJour = parJour.get(curseur) ?? [];
    for (const m of duJour) {
      solde += m.type === "CREDIT" ? Number(m.montant) : -Number(m.montant);
    }
    points.push({ date: curseur, solde, mouvements: duJour, passe: false });
    curseur = jourSuivant(curseur);
    garde++;
  }
  return points;
}

export default function CourbeSolde({
  soldeReel,
  dateMaj,
  dateProjection,
  mouvements,
  seuil,
  historique,
}: Props) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [survol, setSurvol] = useState<number | null>(null);

  const points = useMemo(() => {
    const futur = construireSerie(soldeReel, dateMaj, dateProjection, mouvements);
    if (!historique || historique.length === 0) return futur;
    // Les points d historique anterieurs a la projection, sans doublonner
    // le jour de bascule qui appartient deja a la serie projetee.
    const passe: Point[] = historique
      .filter((h) => h.date < dateMaj)
      .map((h) => ({
        date: h.date,
        solde: Number(h.soldeReel),
        mouvements: [],
        passe: true,
      }));
    return [...passe, ...futur];
  }, [soldeReel, dateMaj, dateProjection, mouvements, historique]);

  if (points.length < 2) return null;

  const soldes = points.map((p) => p.solde);
  let bas = Math.min(...soldes, 0);
  let haut = Math.max(...soldes, 0);
  if (seuil != null) {
    bas = Math.min(bas, seuil);
    haut = Math.max(haut, seuil);
  }
  if (haut === bas) haut = bas + 1;
  const marge = (haut - bas) * 0.12;
  bas -= marge;
  haut += marge;

  const x = (i: number) =>
    MARGE_G + (i * (L - MARGE_G - MARGE_D)) / (points.length - 1);
  const y = (v: number) =>
    MARGE_H + ((haut - v) * (H - MARGE_H - MARGE_B)) / (haut - bas);

  const ligne = points.map((p, i) => `${x(i)},${y(p.solde)}`).join(" ");
  const aire = `${x(0)},${y(bas)} ${ligne} ${x(points.length - 1)},${y(bas)}`;

  // Index du premier point projete : c est la frontiere passe / futur.
  const iBascule = points.findIndex((p) => !p.passe);
  const aDuPasse = iBascule > 0;
  const tracePasse = aDuPasse
    ? points.slice(0, iBascule + 1).map((p, i) => `${x(i)},${y(p.solde)}`).join(" ")
    : "";
  const traceFutur = points
    .map((p, i) => (i >= iBascule ? `${x(i)},${y(p.solde)}` : null))
    .filter(Boolean)
    .join(" ");

  /* Point bas de la periode : c est l information qui compte vraiment. */
  let iMin = 0;
  for (let i = 1; i < points.length; i++) {
    if (points[i].solde < points[iMin].solde) iMin = i;
  }
  const minimum = points[iMin];
  const passeSousZero = minimum.solde < 0;
  const passeSousSeuil = seuil != null && minimum.solde < seuil;
  const couleur = passeSousZero
    ? "var(--debit)"
    : passeSousSeuil
      ? "var(--alerte-bord)"
      : "var(--credit)";

  const surPointeur = (e: React.PointerEvent<SVGSVGElement>) => {
    const svg = svgRef.current;
    if (!svg) return;
    const boite = svg.getBoundingClientRect();
    const relatif = ((e.clientX - boite.left) / boite.width) * L;
    const ratio = (relatif - MARGE_G) / (L - MARGE_G - MARGE_D);
    const i = Math.round(ratio * (points.length - 1));
    setSurvol(Math.max(0, Math.min(points.length - 1, i)));
  };

  const actif = survol != null ? points[survol] : null;

  return (
    <div className="courbe">
      <div className="courbe-tete">
        <span className="courbe-titre">Trajectoire du solde</span>
        {actif ? (
          <span className="courbe-lecture">
            {dateFr(actif.date)} ·{" "}
            <strong className={actif.solde < 0 ? "texte-debit" : ""}>
              {euros(actif.solde)}
            </strong>
            {actif.mouvements.length > 0 && (
              <em className="courbe-detail">
                {actif.mouvements
                  .map(
                    (m) =>
                      (m.type === "DEBIT" ? "−" : "+") + " " + m.libelle
                  )
                  .join(" · ")}
              </em>
            )}
          </span>
        ) : (
          <span className="courbe-lecture">
            Point bas <strong className={passeSousZero ? "texte-debit" : ""}>
              {euros(minimum.solde)}
            </strong>{" "}
            le {dateFr(minimum.date)}
          </span>
        )}
      </div>

      <svg
        ref={svgRef}
        className="courbe-svg"
        viewBox={`0 0 ${L} ${H}`}
        role="img"
        aria-label={
          "Solde projete du " +
          dateFr(dateMaj) +
          " au " +
          dateFr(dateProjection) +
          ", point bas " +
          euros(minimum.solde) +
          " le " +
          dateFr(minimum.date)
        }
        onPointerMove={surPointeur}
        onPointerLeave={() => setSurvol(null)}
      >
        <defs>
          <linearGradient id="degrade-solde" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={couleur} stopOpacity="0.28" />
            <stop offset="100%" stopColor={couleur} stopOpacity="0.02" />
          </linearGradient>
        </defs>

        {/* zone negative, s il y en a une */}
        {bas < 0 && (
          <rect
            x={MARGE_G}
            y={y(0)}
            width={L - MARGE_G - MARGE_D}
            height={Math.max(0, y(bas) - y(0))}
            fill="var(--debit)"
            opacity="0.06"
          />
        )}

        {/* seuil d alerte */}
        {seuil != null && (
          <g>
            <line
              x1={MARGE_G}
              y1={y(seuil)}
              x2={L - MARGE_D}
              y2={y(seuil)}
              stroke="var(--alerte-bord)"
              strokeWidth="1"
              strokeDasharray="2 5"
              opacity="0.7"
            />
            <text
              x={L - MARGE_D}
              y={y(seuil) - 5}
              textAnchor="end"
              className="courbe-etiquette"
              fill="var(--alerte-bord)"
            >
              seuil
            </text>
          </g>
        )}

        {/* zero */}
        <line
          x1={MARGE_G}
          y1={y(0)}
          x2={L - MARGE_D}
          y2={y(0)}
          stroke="var(--filet)"
          strokeWidth="1"
        />

        <polygon points={aire} fill="url(#degrade-solde)" />

        {/* Le passe : constate, donc trait plein et couleur neutre. */}
        {aDuPasse && (
          <polyline
            points={tracePasse}
            fill="none"
            stroke="var(--encre-douce)"
            strokeWidth="2"
            strokeLinejoin="round"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />
        )}

        {/* Le futur : projete, donc pointille. */}
        <polyline
          points={aDuPasse ? traceFutur : ligne}
          fill="none"
          stroke={couleur}
          strokeWidth="2.5"
          strokeDasharray={aDuPasse ? "6 4" : undefined}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />

        {/* Marque de bascule entre constate et projete. */}
        {aDuPasse && (
          <line
            x1={x(iBascule)}
            y1={MARGE_H}
            x2={x(iBascule)}
            y2={H - MARGE_B}
            stroke="var(--filet)"
            strokeWidth="1"
          />
        )}

        {/* point bas */}
        <circle cx={x(iMin)} cy={y(minimum.solde)} r="4" fill={couleur} />

        {/* curseur de survol */}
        {actif && survol != null && (
          <g pointerEvents="none">
            <line
              x1={x(survol)}
              y1={MARGE_H}
              x2={x(survol)}
              y2={H - MARGE_B}
              stroke="var(--encre-douce)"
              strokeWidth="1"
              strokeDasharray="3 3"
            />
            <circle
              cx={x(survol)}
              cy={y(actif.solde)}
              r="4.5"
              fill="var(--papier)"
              stroke={couleur}
              strokeWidth="2.5"
            />
          </g>
        )}

        <text
          x={MARGE_G}
          y={H - 6}
          className="courbe-etiquette"
          fill="var(--encre-douce)"
        >
          {dateFr(points[0].date)}
        </text>
        <text
          x={L - MARGE_D}
          y={H - 6}
          textAnchor="end"
          className="courbe-etiquette"
          fill="var(--encre-douce)"
        >
          {dateFr(points[points.length - 1].date)}
        </text>
      </svg>
    </div>
  );
}
