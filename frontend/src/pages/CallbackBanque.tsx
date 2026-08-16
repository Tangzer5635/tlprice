import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";

/** Page de retour de la banque : echange le code contre une session. */
export default function CallbackBanque() {
  const [params] = useSearchParams();
  const naviguer = useNavigate();
  const lance = useRef(false);

  useEffect(() => {
    if (lance.current) return;
    lance.current = true;
    const code = params.get("code");
    const state = params.get("state");
    if (!code || !state) {
      naviguer("/banque?erreur=" + encodeURIComponent(
        "Retour de la banque incomplet. Relance la connexion."), { replace: true });
      return;
    }
    api
      .post("/api/banque/callback", { code, state })
      .then(() => naviguer("/banque?connecte=oui", { replace: true }))
      .catch((e) =>
        naviguer("/banque?erreur=" + encodeURIComponent(e.message), { replace: true })
      );
  }, [params, naviguer]);

  return <p className="attente">Finalisation de la connexion a ta banque…</p>;
}
