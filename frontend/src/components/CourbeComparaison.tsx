import { useRef, useState } from "react";
import { dateFr, euros } from "../api";
import type { LigneApi, Point } from "../scenarioCalcul";

type Props = {
  points: Point[];
  seuil: number | null;
  evenements: LigneApi[];
  couleur: string;
};

const L = 700;
const H = 240;
const MG = 8;
const MD = 8;
const MH = 22;
const MB = 24;

/**
 * Deux trajectoires sur le meme graphique : sans le scenario (gris,
 * pointille) et avec (en couleur). La surface entre les deux, c est ce que
 * coute — ou rapporte — l hypothese, jour apres jour.
 */
export default function CourbeComparaison({ points, seuil, evenements, couleur }: Props) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [survol, setSurvol] = useState<number | null>(null);

  if (points.length < 2) return null;

  const valeurs = points.flatMap((p) => [p.reel, p.avec]);
  let bas = Math.min(...valeurs, 0);
  let haut = Math.max(...valeurs, 0);
  if (seuil != null) {
    bas = Math.min(bas, seuil);
    haut = Math.max(haut, seuil);
  }
  if (haut === bas) haut = bas + 1;
  const marge = (haut - bas) * 0.12;
  bas -= marge;
  haut += marge;

  const n = points.length;
  const x = (i: number) => MG + (i * (L - MG - MD)) / (n - 1);
  const y = (v: number) => MH + ((haut - v) * (H - MH - MB)) / (haut - bas);
  const index = new Map(points.map((p, i) => [p.date, i]));

  const traceReel = points.map((p, i) => `${x(i)},${y(p.reel)}`).join(" ");
  const traceAvec = points.map((p, i) => `${x(i)},${y(p.avec)}`).join(" ");
  const ecart =
    traceAvec +
    " " +
    points
      .map((p, i) => `${x(i)},${y(p.reel)}`)
      .reverse()
      .join(" ");
  const fin = points[n - 1];
  const couteux = fin.avec < fin.reel;

  // Un repere par jour d evenement ; etiquettes seulement s il y en a peu.
  const parJour = new Map<string, number>();
  for (const e of evenements) {
    if (!index.has(e.date)) continue;
    parJour.set(e.date, (parJour.get(e.date) ?? 0) + (e.type === "CREDIT" ? e.montant : -e.montant));
  }
  const reperes = Array.from(parJour.entries());
  const etiquettes = reperes.length <= 6;

  const surPointeur = (e: React.PointerEvent<SVGSVGElement>) => {
    const svg = svgRef.current;
    if (!svg) return;
    const boite = svg.getBoundingClientRect();
    const relatif = ((e.clientX - boite.left) / boite.width) * L;
    const i = Math.round(((relatif - MG) / (L - MG - MD)) * (n - 1));
    setSurvol(Math.max(0, Math.min(n - 1, i)));
  };
  const actif = survol != null ? points[survol] : null;

  return (
    <div className="courbe comparaison">
      <div className="courbe-tete">
        <span className="legende">
          <i className="legende-trait legende-reel" /> Sans
          <i className="legende-trait" style={{ background: couleur }} /> Avec
        </span>
        {actif && (
          <span className="courbe-lecture">
            {dateFr(actif.date)} · <span className="texte-doux">{euros(actif.reel)}</span> →{" "}
            <strong className={actif.avec < 0 ? "texte-debit" : ""}>{euros(actif.avec)}</strong>
          </span>
        )}
      </div>

      <svg
        ref={svgRef}
        className="courbe-svg"
        viewBox={`0 0 ${L} ${H}`}
        role="img"
        aria-label={`Solde sans le scenario ${euros(fin.reel)}, avec ${euros(fin.avec)} au ${dateFr(fin.date)}`}
        onPointerDown={surPointeur}
        onPointerMove={surPointeur}
        onPointerLeave={() => setSurvol(null)}
      >
        {bas < 0 && (
          <rect
            x={MG}
            y={y(0)}
            width={L - MG - MD}
            height={Math.max(0, y(bas) - y(0))}
            fill="var(--debit)"
            opacity="0.07"
          />
        )}
        <line x1={MG} y1={y(0)} x2={L - MD} y2={y(0)} stroke="var(--filet)" strokeWidth="1" />
        {seuil != null && (
          <g>
            <line
              x1={MG}
              y1={y(seuil)}
              x2={L - MD}
              y2={y(seuil)}
              stroke="var(--alerte-bord)"
              strokeWidth="1"
              strokeDasharray="2 5"
            />
            <text x={L - MD} y={y(seuil) - 5} textAnchor="end" className="courbe-etiquette" fill="var(--alerte-bord)">
              seuil {euros(seuil)}
            </text>
          </g>
        )}

        <polygon points={ecart} fill={couteux ? "var(--debit)" : "var(--credit)"} opacity="0.13" />

        <polyline
          points={traceReel}
          fill="none"
          stroke="var(--encre-douce)"
          strokeWidth="1.6"
          strokeDasharray="5 4"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        <polyline
          points={traceAvec}
          fill="none"
          stroke={couleur}
          strokeWidth="2.8"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />

        {reperes.map(([date, net]) => {
          const i = index.get(date)!;
          const p = points[i];
          return (
            <g key={date}>
              <line
                x1={x(i)}
                y1={y(p.reel)}
                x2={x(i)}
                y2={y(p.avec)}
                stroke={net < 0 ? "var(--debit)" : "var(--credit)"}
                strokeWidth="1.5"
                opacity="0.6"
              />
              <circle cx={x(i)} cy={y(p.avec)} r="5" fill="var(--papier)" stroke={net < 0 ? "var(--debit)" : "var(--credit)"} strokeWidth="2.5" />
              {etiquettes && (
                <text
                  x={Math.min(Math.max(x(i), 40), L - 40)}
                  y={Math.max(y(Math.max(p.avec, p.reel)) - 10, 14)}
                  textAnchor="middle"
                  className="courbe-etiquette etiquette-evenement"
                  fill={net < 0 ? "var(--debit)" : "var(--credit)"}
                >
                  {(net < 0 ? "−" : "+") + Math.round(Math.abs(net)) + " €"}
                </text>
              )}
            </g>
          );
        })}

        {actif && survol != null && (
          <g pointerEvents="none">
            <line x1={x(survol)} y1={MH} x2={x(survol)} y2={H - MB} stroke="var(--encre-douce)" strokeWidth="1" strokeDasharray="3 3" />
            <circle cx={x(survol)} cy={y(actif.reel)} r="3.5" fill="var(--encre-douce)" />
            <circle cx={x(survol)} cy={y(actif.avec)} r="5" fill="var(--papier)" stroke={couleur} strokeWidth="2.5" />
          </g>
        )}

        <text x={MG} y={H - 6} className="courbe-etiquette" fill="var(--encre-douce)">
          {dateFr(points[0].date)}
        </text>
        <text x={L - MD} y={H - 6} textAnchor="end" className="courbe-etiquette" fill="var(--encre-douce)">
          {dateFr(fin.date)}
        </text>
      </svg>
    </div>
  );
}
