import { useEffect, useState } from "react";
import { AlerteSolde, api, ConfigSmtp, ErreurApi, euros, ParametresApp } from "../api";
import BlocSauvegarde from "../components/BlocSauvegarde";
import BlocSauvegardesAuto from "../components/BlocSauvegardesAuto";
import BlocAppareils from "../components/BlocAppareils";
import {
  abonnementActuel,
  activerPush,
  desactiverPush,
  estIOS,
  estInstallee,
  pushSupporte,
} from "../push";

type Theme = "clair" | "sombre";

const appliquerTheme = (t: Theme) => {
  document.documentElement.setAttribute("data-theme", t);
  localStorage.setItem("tlprice-theme", t);
};

/** Preselections pour les fournisseurs SMTP courants. */
const PRESETS: Record<string, { host: string; port: number; starttls: boolean }> = {
  Gmail: { host: "smtp.gmail.com", port: 587, starttls: true },
  Outlook: { host: "smtp.office365.com", port: 587, starttls: true },
  Yahoo: { host: "smtp.mail.yahoo.com", port: 587, starttls: true },
  iCloud: { host: "smtp.mail.me.com", port: 587, starttls: true },
};

export default function Parametres() {
  const [theme, setTheme] = useState<Theme>(
    (localStorage.getItem("tlprice-theme") as Theme) || "sombre"
  );
  const [parametres, setParametres] = useState<ParametresApp | null>(null);
  const [email, setEmail] = useState("");
  const [seuil, setSeuil] = useState("");
  const [heureRecap, setHeureRecap] = useState(8);
  const [heureRecapPush, setHeureRecapPush] = useState(7);
  const [pushSiVide, setPushSiVide] = useState(false);
  const [pushAbonne, setPushAbonne] = useState(false);
  const [pushServeur, setPushServeur] = useState(false);
  const [pushOccupe, setPushOccupe] = useState(false);
  const [solde, setSolde] = useState("");
  const [soldeMaj, setSoldeMaj] = useState<string | null>(null);
  const [banqueConnectee, setBanqueConnectee] = useState(false);
  const [alerte, setAlerte] = useState<AlerteSolde | null>(null);
  const [mailDispo, setMailDispo] = useState(false);

  // Config SMTP
  const [smtp, setSmtp] = useState<ConfigSmtp | null>(null);
  const [smtpHost, setSmtpHost] = useState("");
  const [smtpPort, setSmtpPort] = useState("587");
  const [smtpUser, setSmtpUser] = useState("");
  const [smtpPass, setSmtpPass] = useState("");
  const [smtpFrom, setSmtpFrom] = useState("");
  const [smtpStarttls, setSmtpStarttls] = useState(true);

  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  const rechargerAlerte = () => api.get("/api/parametres/alerte").then(setAlerte).catch(() => {});
  const rechargerMailDispo = () => api.get("/api/parametres/mail-disponible")
    .then((r) => setMailDispo(r.disponible));
  const rechargerSmtp = () => api.get("/api/parametres/smtp").then((c: ConfigSmtp) => {
    setSmtp(c);
    setSmtpHost(c.host ?? "");
    setSmtpPort(String(c.port ?? 587));
    setSmtpUser(c.username ?? "");
    setSmtpFrom(c.expediteur ?? "");
    setSmtpStarttls(c.starttls ?? true);
    setSmtpPass("");
  });

  useEffect(() => {
    api.get("/api/parametres").then((p) => {
      setParametres(p);
      setEmail(p.email ?? "");
      setSeuil(p.seuilAlerte !== null ? String(p.seuilAlerte) : "");
      setHeureRecap(p.heureRecap ?? 8);
      setHeureRecapPush(p.heureRecapPush ?? 7);
      setPushSiVide(p.pushSiVide ?? false);
    }).catch((e) => setErreur(e.message));
    api.get("/api/push/cle-publique")
      .then((r) => setPushServeur(!!r.disponible))
      .catch(() => setPushServeur(false));
    abonnementActuel().then((a) => setPushAbonne(!!a));
    api.get("/api/solde").then((c) => {
      setSolde(String(c.solde));
      setSoldeMaj(c.dateMaj);
    }).catch(() => {});
    api.get("/api/banque/statut")
      .then((s) => setBanqueConnectee(!!s.connectee))
      .catch(() => {});
    rechargerAlerte();
    rechargerMailDispo();
    rechargerSmtp();
  }, []);

  const changerTheme = (t: Theme) => {
    setTheme(t);
    appliquerTheme(t);
  };

  const seuilNumerique = (): number | null => {
    const brut = seuil.trim().replace(",", ".");
    if (brut === "") return null;
    const valeur = parseFloat(brut);
    return isNaN(valeur) ? null : valeur;
  };

  /**
   * Un seul point d envoi pour tous les reglages : l API attend l objet
   * complet, envoyer un champ seul ecraserait les autres avec les valeurs
   * par defaut du DTO.
   */
  const sauver = async (modifs: Partial<ParametresApp> = {}) => {
    setErreur("");
    setMessage("");
    const corps = {
      email: email.trim(),
      alertesEmail: parametres?.alertesEmail ?? false,
      seuilAlerte: seuilNumerique(),
      heureRecap,
      pushActif: parametres?.pushActif ?? false,
      heureRecapPush,
      pushSiVide,
      ...modifs,
    };
    try {
      const maj = await api.put("/api/parametres", corps);
      setParametres(maj);
      setMessage("Parametres enregistres.");
      await rechargerAlerte();
      return maj;
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
      return null;
    }
  };

  const enregistrer = async (alertesEmail: boolean, heure?: number) =>
    sauver({ alertesEmail, ...(heure !== undefined ? { heureRecap: heure } : {}) });

  const changerHeure = async (h: number) => {
    setHeureRecap(h);
    await sauver({ heureRecap: h });
  };

  const changerHeurePush = async (h: number) => {
    setHeureRecapPush(h);
    await sauver({ heureRecapPush: h });
  };

  const basculerPush = async () => {
    setPushOccupe(true);
    setErreur("");
    setMessage("");
    try {
      if (pushAbonne) {
        await desactiverPush();
        setPushAbonne(false);
        await sauver({ pushActif: false });
        setMessage("Notifications desactivees sur cet appareil.");
      } else {
        const probleme = await activerPush();
        if (probleme) {
          setErreur(probleme);
          return;
        }
        setPushAbonne(true);
        await sauver({ pushActif: true });
        setMessage("Cet appareil recevra le rappel quotidien.");
      }
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setPushOccupe(false);
    }
  };

  const enregistrerSolde = async () => {
    setErreur("");
    setMessage("");
    try {
      const c = await api.put("/api/solde", {
        solde: Number(solde.replace(",", ".")),
      });
      setSolde(String(c.solde));
      setSoldeMaj(c.dateMaj);
      setMessage("Solde enregistre.");
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const testerPush = async () => {
    setPushOccupe(true);
    try {
      const r = await api.post("/api/push/test");
      setMessage(r.statut);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setPushOccupe(false);
    }
  };

  const preselection = (nom: string) => {
    const p = PRESETS[nom];
    if (!p) return;
    setSmtpHost(p.host);
    setSmtpPort(String(p.port));
    setSmtpStarttls(p.starttls);
  };

  const enregistrerSmtp = async () => {
    setErreur("");
    setMessage("");
    try {
      const maj = await api.put("/api/parametres/smtp", {
        host: smtpHost.trim(),
        port: Number(smtpPort),
        username: smtpUser.trim(),
        motDePasse: smtpPass || null,
        expediteur: smtpFrom.trim() || null,
        starttls: smtpStarttls,
      });
      setSmtp(maj);
      setSmtpPass("");
      setMessage("Configuration SMTP enregistree.");
      await rechargerMailDispo();
    } catch (e) {
      setErreur(e instanceof ErreurApi ? e.message : "Erreur");
    }
  };

  const supprimerSmtp = async () => {
    setErreur("");
    try {
      await api.supprimer("/api/parametres/smtp");
      await rechargerSmtp();
      await rechargerMailDispo();
      setMessage("Configuration SMTP supprimee.");
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const tester = async () => {
    setErreur("");
    setMessage("");
    try {
      const r = await api.post("/api/parametres/test-email");
      setMessage(r.statut);
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  return (
    <div>
      <h1 className="titre-page">Parametres</h1>
      {message && <p className="message-info">{message}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}

      {/* ---- Theme ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Apparence</h2>
        <div className="choix-theme">
          <button
            className={"vignette-theme" + (theme === "sombre" ? " active" : "")}
            onClick={() => changerTheme("sombre")}
          >
            <span className="apercu apercu-sombre">
              <span className="apercu-marque">tlprice·</span>
              <span className="apercu-montant">1 234,00 €</span>
            </span>
            <strong>Sombre</strong>
          </button>
          <button
            className={"vignette-theme" + (theme === "clair" ? " active" : "")}
            onClick={() => changerTheme("clair")}
          >
            <span className="apercu apercu-clair">
              <span className="apercu-marque">tlprice·</span>
              <span className="apercu-montant">1 234,00 €</span>
            </span>
            <strong>Clair</strong>
          </button>
        </div>
      </section>

      {/* ---- Seuil ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Seuil d alerte</h2>
        <p className="note">
          Si ton solde projete des 30 prochains jours passe sous ce montant,
          un avertissement s affiche sur le tableau de bord et dans le recap
          email.
        </p>
        {alerte && alerte.seuil !== null && (
          <p className={"resume-seuil " + (alerte.alerte ? "seuil-alerte" : "")}>
            Solde minimum sur 30 jours : <strong>{euros(alerte.soldeMinimum ?? 0)}</strong>
            {alerte.dateMinimum && " le " + alerte.dateMinimum}
          </p>
        )}
        <div className="ligne-champ">
          <input
            inputMode="decimal"
            placeholder="ex : 100 (vide pour desactiver)"
            value={seuil}
            onChange={(e) => setSeuil(e.target.value)}
          />
          <button
            className="bouton"
            onClick={() => enregistrer(parametres?.alertesEmail ?? false)}
          >
            Enregistrer
          </button>
        </div>
      </section>

      {/* ---- Configuration SMTP ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Configuration SMTP</h2>
        <p className="note">
          Pour recevoir le recap par email, entre ici les identifiants du
          serveur d envoi. {smtp?.configuree && "Deja configure ✓"}
        </p>
        <div className="ligne-champ" style={{ marginBottom: 12 }}>
          <span className="etiquette">Presets :</span>
          {Object.keys(PRESETS).map((nom) => (
            <button key={nom} className="puce" onClick={() => preselection(nom)}>
              {nom}
            </button>
          ))}
        </div>
        <div className="formulaire">
          <label>
            Serveur SMTP
            <input
              value={smtpHost}
              onChange={(e) => setSmtpHost(e.target.value)}
              placeholder="smtp.gmail.com"
            />
          </label>
          <label>
            Port
            <input
              type="number"
              value={smtpPort}
              onChange={(e) => setSmtpPort(e.target.value)}
            />
          </label>
          <label>
            Utilisateur (souvent = adresse email)
            <input
              value={smtpUser}
              onChange={(e) => setSmtpUser(e.target.value)}
              placeholder="ton.adresse@gmail.com"
            />
          </label>
          <label>
            Mot de passe {smtp?.configuree && <em>(vide pour garder l ancien)</em>}
            <input
              type="password"
              value={smtpPass}
              onChange={(e) => setSmtpPass(e.target.value)}
              placeholder={smtp?.configuree ? "•••••••• (deja enregistre)" : "xxxx xxxx xxxx xxxx"}
              autoComplete="new-password"
            />
            <em className="note-inline">
              Pour Gmail : Compte Google → Securite → Validation en 2 etapes →
              Mots de passe des applications. C est 16 caracteres, sans espaces
              une fois colles.
            </em>
          </label>
          <label>
            Adresse d expedition <em>(optionnel)</em>
            <input
              type="email"
              value={smtpFrom}
              onChange={(e) => setSmtpFrom(e.target.value)}
              placeholder="Par defaut : identique a l utilisateur"
            />
          </label>
          <label className="ligne-interrupteur">
            <input
              type="checkbox"
              checked={smtpStarttls}
              onChange={(e) => setSmtpStarttls(e.target.checked)}
            />
            <span>Utiliser STARTTLS (recommande, actif par defaut)</span>
          </label>
          <div className="actions-modal">
            {smtp?.configuree && (
              <button className="bouton-texte danger" onClick={supprimerSmtp}>
                Supprimer la configuration
              </button>
            )}
            <button className="bouton" onClick={enregistrerSmtp}>
              Enregistrer
            </button>
          </div>
        </div>
      </section>

      {/* ---- Solde du compte ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Solde du compte</h2>
        {banqueConnectee ? (
          <div className="avertissement">
            Ta banque est connectee : ce solde est ecrase a chaque
            synchronisation. Ne le corrige ici que pour depanner, par exemple
            si la banque renvoie un montant visiblement faux.
          </div>
        ) : (
          <p className="note">
            Sans banque connectee, c est ce chiffre qui sert de base a toutes
            les projections. Reporte-le depuis ton appli bancaire.
          </p>
        )}
        <div className="ligne-champ">
          <input
            type="text"
            inputMode="decimal"
            placeholder="0,00"
            value={solde}
            onChange={(e) => setSolde(e.target.value)}
            aria-label="Solde du compte en euros"
          />
          <button className="bouton" onClick={enregistrerSolde}>
            Enregistrer
          </button>
        </div>
        {soldeMaj && (
          <p className="note-inline">Derniere mise a jour : {soldeMaj}</p>
        )}
      </section>

      {/* ---- Recap email ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Recap par email</h2>
        {!mailDispo && (
          <p className="message-info">
            Configure d abord ton SMTP ci-dessus pour activer le recap.
          </p>
        )}
        <p className="note">
          Un mail par jour a l heure que tu choisis : solde banque, projection
          a 7 jours et mouvements de la semaine. Cette heure est independante
          de celle des notifications iPhone.
        </p>
        <div className="ligne-champ">
          <input
            type="email"
            placeholder="Adresse de reception"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={!mailDispo}
          />
          <button
            className="bouton-texte"
            onClick={tester}
            disabled={!mailDispo || !email}
          >
            Envoyer un test
          </button>
        </div>
        <label className="ligne-interrupteur">
          <input
            type="checkbox"
            checked={parametres?.alertesEmail ?? false}
            onChange={(e) => enregistrer(e.target.checked)}
            disabled={!mailDispo}
          />
          <span>Recevoir le recap chaque jour</span>
        </label>
        <div className="ligne-champ" style={{ marginTop: 8 }}>
          <span className="etiquette">Heure d envoi</span>
          <select
            value={heureRecap}
            onChange={(e) => changerHeure(Number(e.target.value))}
            disabled={!mailDispo}
          >
            {Array.from({ length: 24 }, (_, h) => (
              <option key={h} value={h}>
                {String(h).padStart(2, "0")}:00
              </option>
            ))}
          </select>
          <span className="note-inline">Heure de Paris.</span>
        </div>
      </section>

      {/* ---- Notifications push (iPhone / navigateur) ---- */}
      <section className="carte-parametre">
        <h2 className="titre-section">Notifications sur le telephone</h2>
        <p className="note">
          Un rappel par jour a l heure choisie, avec les prelevements du jour
          et le solde projete. Envoye par le serveur : pas besoin d avoir
          l app ouverte.
        </p>

        {!pushServeur && (
          <p className="message-info">
            Le serveur n a pas de cles VAPID. Genere-les une fois avec{" "}
            <code className="copiable">npx web-push generate-vapid-keys</code>{" "}
            puis renseigne <code className="copiable">TLPRICE_VAPID_CLE_PUBLIQUE</code>{" "}
            et <code className="copiable">TLPRICE_VAPID_CLE_PRIVEE</code> dans le{" "}
            <code className="copiable">.env</code>.
          </p>
        )}

        {pushServeur && estIOS() && !estInstallee() && (
          <div className="avertissement">
            <strong>Etape obligatoire sur iPhone.</strong> Safari ne delivre
            les notifications qu aux apps installees. Dans Safari : bouton
            Partager → <em>Sur l ecran d accueil</em>, puis rouvre tlprice
            depuis l icone et reviens ici.
          </div>
        )}

        {pushServeur && !pushSupporte() && !(estIOS() && !estInstallee()) && (
          <p className="message-erreur">
            Ce navigateur ne gere pas les notifications push.
          </p>
        )}

        {pushServeur && (
          <>
            <div className="ligne-champ">
              <button
                className={pushAbonne ? "bouton secondaire" : "bouton"}
                onClick={basculerPush}
                disabled={pushOccupe}
              >
                {pushOccupe
                  ? "…"
                  : pushAbonne
                    ? "Desactiver sur cet appareil"
                    : "Activer sur cet appareil"}
              </button>
              {pushAbonne && (
                <button
                  className="bouton-texte"
                  onClick={testerPush}
                  disabled={pushOccupe}
                >
                  Envoyer un test
                </button>
              )}
            </div>

            <label className="champ-inline">
              <span>Heure du rappel</span>
              <select
                value={heureRecapPush}
                onChange={(e) => changerHeurePush(Number(e.target.value))}
              >
                {Array.from({ length: 24 }, (_, h) => (
                  <option key={h} value={h}>
                    {String(h).padStart(2, "0")}:00
                  </option>
                ))}
              </select>
              <em className="note-inline">
                Heure de Paris, appliquee aux appareils qui n ont pas la leur.
                Independante du mail ({String(heureRecap).padStart(2, "0")}:00).
              </em>
            </label>

            <label className="ligne-interrupteur">
              <input
                type="checkbox"
                checked={pushSiVide}
                onChange={(e) => {
                  setPushSiVide(e.target.checked);
                  sauver({ pushSiVide: e.target.checked });
                }}
              />
              <span>Notifier aussi les jours sans mouvement</span>
            </label>

            <BlocAppareils
              heureCompte={heureRecapPush}
              onAppareilCourantRetire={() => setPushAbonne(false)}
            />

            <p className="note">
              Chaque appareil s active separement et peut avoir sa propre heure.
              Le contenu de la notification, lui, reste commun.
            </p>
          </>
        )}
      </section>

      {/* ---- Sauvegarde / transfert entre instances ---- */}
      <BlocSauvegarde />
      <BlocSauvegardesAuto />
    </div>
  );
}
