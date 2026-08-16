import { ReactNode, useEffect } from "react";

type Props = {
  titre?: ReactNode;
  onFermer: () => void;
  children: ReactNode;
};

/**
 * Une fenetre qui monte depuis le bas de l ecran.
 *
 * Une modale centree oblige le pouce a remonter au milieu de l ecran pour
 * agir, et son bouton de fermeture atterrit dans le coin le plus eloigne du
 * pouce. La feuille basse garde tout ce qui est actionnable dans la moitie
 * inferieure, la seule reellement atteignable a une main sur un grand
 * telephone.
 *
 * Sur grand ecran, le CSS la recentre : c est la meme fenetre qu avant.
 */
export default function FeuilleBasse({ titre, onFermer, children }: Props) {
  // Le fond ne doit pas defiler derriere la feuille, et Echap la ferme.
  useEffect(() => {
    document.body.style.overflow = "hidden";
    const touche = (e: KeyboardEvent) => {
      if (e.key === "Escape") onFermer();
    };
    document.addEventListener("keydown", touche);
    return () => {
      document.body.style.overflow = "";
      document.removeEventListener("keydown", touche);
    };
  }, [onFermer]);

  return (
    <div className="fb-voile" onClick={onFermer}>
      <div
        className="fb-carte"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <span className="fb-poignee" aria-hidden="true" />
        {titre && <div className="fb-tete">{titre}</div>}
        <div className="fb-corps">{children}</div>
        <button className="fb-fermer" onClick={onFermer}>
          Fermer
        </button>
      </div>
    </div>
  );
}
