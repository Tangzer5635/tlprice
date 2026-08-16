import { ReactNode, useEffect, useRef, useState } from "react";

type Props = {
  onRafraichir: () => Promise<unknown>;
  /** Desactive le geste quand rien n est branche (pas de banque connectee). */
  actif?: boolean;
  children: ReactNode;
};

/** Au-dela de cette distance, le relachement declenche le rafraichissement. */
const SEUIL = 70;

/** Le doigt parcourt deux fois plus de chemin que l indicateur : ca freine. */
const FREIN = 0.5;

/**
 * Le geste iOS attendu : on tire vers le bas en haut de page pour
 * rafraichir. Sur mobile, aller chercher un bouton "Synchroniser" demande de
 * viser une cible precise pour une action qu on fait plusieurs fois par jour ;
 * le geste, lui, ne demande rien qu on ait a trouver des yeux.
 *
 * Le bouton reste present sur grand ecran, ou il n y a pas de geste tactile.
 */
export default function TirerPourRafraichir({ onRafraichir, actif = true, children }: Props) {
  const [distance, setDistance] = useState(0);
  const [encours, setEncours] = useState(false);
  const depart = useRef<number | null>(null);
  const boite = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = boite.current;
    if (!element || !actif) return;

    const debut = (e: TouchEvent) => {
      // Seulement si la page est deja tout en haut : sinon on interfere
      // avec le defilement normal.
      if (window.scrollY > 0 || encours) return;
      depart.current = e.touches[0].clientY;
    };

    const bouge = (e: TouchEvent) => {
      if (depart.current === null) return;
      const delta = e.touches[0].clientY - depart.current;
      if (delta <= 0) {
        // Le doigt remonte : on abandonne, c est un defilement.
        depart.current = null;
        setDistance(0);
        return;
      }
      setDistance(Math.min(delta * FREIN, SEUIL + 30));
    };

    const fin = async () => {
      if (depart.current === null) return;
      const parcouru = distance;
      depart.current = null;
      if (parcouru < SEUIL) {
        setDistance(0);
        return;
      }
      setEncours(true);
      setDistance(SEUIL);
      try {
        await onRafraichir();
      } finally {
        setEncours(false);
        setDistance(0);
      }
    };

    // Passif : on ne bloque jamais le defilement natif. Le rebond iOS est
    // deja neutralise par overscroll-behavior-y sur le body.
    element.addEventListener("touchstart", debut, { passive: true });
    element.addEventListener("touchmove", bouge, { passive: true });
    element.addEventListener("touchend", fin);
    element.addEventListener("touchcancel", fin);
    return () => {
      element.removeEventListener("touchstart", debut);
      element.removeEventListener("touchmove", bouge);
      element.removeEventListener("touchend", fin);
      element.removeEventListener("touchcancel", fin);
    };
  }, [distance, encours, actif, onRafraichir]);

  const pret = distance >= SEUIL;

  return (
    <div ref={boite} className="tirer-boite">
      <div
        className={"tirer-indicateur" + (encours ? " tourne" : "")}
        style={{ height: distance, opacity: distance > 8 ? 1 : 0 }}
        aria-hidden={!encours}
      >
        <span className="tirer-texte">
          {encours ? "Synchronisation…" : pret ? "Relache pour synchroniser" : "Tire pour synchroniser"}
        </span>
      </div>
      <div style={{ transform: `translateY(${distance}px)` }} className="tirer-contenu">
        {children}
      </div>
    </div>
  );
}
