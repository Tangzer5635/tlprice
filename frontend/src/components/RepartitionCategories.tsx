import { useMemo } from "react";
import { euros, LIBELLES_CATEGORIES } from "../api";

type MouvementCat = {
  montant: number;
  type: "DEBIT" | "CREDIT";
  categorie?: string | null;
};

/* Teintes stables par categorie, lisibles sur les deux themes. */
const TEINTES: Record<string, string> = {
  LOGEMENT: "#4f8ef7",
  ALIMENTATION: "#3fae72",
  TRANSPORT: "#e08a3c",
  ABONNEMENTS: "#9b6ce0",
  SANTE: "#e0607a",
  LOISIRS: "#e0bc3c",
  REVENUS: "#28a17a",
  EPARGNE: "#4bc0c0",
  AUTRE: "#8b9a94",
};

export default function RepartitionCategories({
  mouvements,
}: {
  mouvements: MouvementCat[];
}) {
  const lignes = useMemo(() => {
    const totaux = new Map<string, number>();
    let total = 0;
    for (const m of mouvements) {
      if (m.type !== "DEBIT") continue;
      const cle = m.categorie || "AUTRE";
      totaux.set(cle, (totaux.get(cle) ?? 0) + Number(m.montant));
      total += Number(m.montant);
    }
    if (total === 0) return [];
    const liste = Array.from(totaux.entries()).map(([cle, montant]) => ({
      cle,
      montant,
      part: (montant / total) * 100,
    }));
    liste.sort((a, b) => b.montant - a.montant);
    return liste;
  }, [mouvements]);

  if (lignes.length === 0) return null;

  return (
    <section className="repartition">
      <h2 className="titre-section">Ou part l argent</h2>

      <div
        className="repartition-barre"
        role="img"
        aria-label={lignes
          .map(
            (l) =>
              (LIBELLES_CATEGORIES[l.cle] ?? l.cle) +
              " " +
              Math.round(l.part) +
              "%"
          )
          .join(", ")}
      >
        {lignes.map((l) => (
          <span
            key={l.cle}
            className="repartition-segment"
            style={{
              width: l.part + "%",
              background: TEINTES[l.cle] ?? TEINTES.AUTRE,
            }}
            title={
              (LIBELLES_CATEGORIES[l.cle] ?? l.cle) + " · " + euros(l.montant)
            }
          />
        ))}
      </div>

      <ul className="repartition-legende">
        {lignes.map((l) => (
          <li key={l.cle}>
            <span
              className="repartition-pastille"
              style={{ background: TEINTES[l.cle] ?? TEINTES.AUTRE }}
            />
            <span className="repartition-nom">
              {LIBELLES_CATEGORIES[l.cle] ?? l.cle}
            </span>
            <span className="repartition-montant">{euros(l.montant)}</span>
            <span className="repartition-part">{Math.round(l.part)} %</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
