import { FormEvent, useEffect, useMemo, useState } from "react";
import { api } from "../api";

type Statut = {
  configuree: boolean;
  applicationId: string | null;
  nomBanque: string | null;
  paysBanque: string | null;
  connectee: boolean;
  validUntil: string | null;
};

type Banque = { nom: string; pays: string; logo: string | null };

const PAYS = [
  ["FR", "France"], ["BE", "Belgique"], ["DE", "Allemagne"], ["ES", "Espagne"],
  ["IT", "Italie"], ["NL", "Pays-Bas"], ["PT", "Portugal"], ["LU", "Luxembourg"],
  ["CH", "Suisse"], ["GB", "Royaume-Uni"],
];

export default function Banque() {
  const [statut, setStatut] = useState<Statut | null>(null);
  const [redirectUrl, setRedirectUrl] = useState("");
  // Vrai si le serveur renverrait la banque vers localhost, ou n a pas
  // d URL de renvoi du tout : dans les deux cas la connexion echouera
  // APRES authentification, quand il sera trop tard pour comprendre.
  const [redirectSuspecte, setRedirectSuspecte] = useState(false);
  const [erreur, setErreur] = useState("");
  const [info, setInfo] = useState("");

  // Etape config
  const [applicationId, setApplicationId] = useState("");
  const [fichierCle, setFichierCle] = useState<File | null>(null);
  const [envoiConfig, setEnvoiConfig] = useState(false);

  // Etape banque
  const [pays, setPays] = useState("FR");
  const [banques, setBanques] = useState<Banque[]>([]);
  const [recherche, setRecherche] = useState("");
  const [chargementBanques, setChargementBanques] = useState(false);

  const chargerStatut = () =>
    api.get("/api/banque/statut").then((s) => {
      setStatut(s);
      return s as Statut;
    });

  useEffect(() => {
    chargerStatut().catch((e) => setErreur(e.message));
    api.get("/api/banque/redirect-url").then((r) => {
      setRedirectUrl(r.url);
      setRedirectSuspecte(Boolean(r.locale || r.absente));
    });
    const params = new URLSearchParams(window.location.search);
    if (params.get("connecte") === "oui") {
      setInfo("Ta banque est connectee. Tu peux synchroniser ton solde depuis l onglet Solde.");
    }
    if (params.get("erreur")) {
      setErreur(params.get("erreur")!);
    }
  }, []);

  const chargerBanques = async (p: string) => {
    setChargementBanques(true);
    setErreur("");
    try {
      setBanques(await api.get("/api/banque/banques?pays=" + p));
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    } finally {
      setChargementBanques(false);
    }
  };

  useEffect(() => {
    if (statut?.configuree && !statut.nomBanque) {
      chargerBanques(pays);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statut?.configuree, statut?.nomBanque, pays]);

  const banquesFiltrees = useMemo(
    () =>
      banques.filter((b) =>
        b.nom.toLowerCase().includes(recherche.toLowerCase())
      ),
    [banques, recherche]
  );

  const envoyerConfig = async (e: FormEvent) => {
    e.preventDefault();
    if (!fichierCle) return;
    setEnvoiConfig(true);
    setErreur("");
    try {
      const donnees = new FormData();
      donnees.set("applicationId", applicationId);
      donnees.set("clePrivee", fichierCle);
      setStatut(await api.postFormulaire("/api/banque/config", donnees));
      setInfo("Identifiants enregistres. Choisis maintenant ta banque.");
    } catch (err) {
      setErreur(err instanceof Error ? err.message : "Erreur");
    } finally {
      setEnvoiConfig(false);
    }
  };

  const choisir = async (b: Banque) => {
    setErreur("");
    try {
      setStatut(await api.post("/api/banque/choisir", { nom: b.nom, pays: b.pays }));
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const connecter = async () => {
    setErreur("");
    try {
      const { url } = await api.get("/api/banque/connexion");
      window.location.href = url;
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const changerBanque = async () => {
    if (statut) setStatut({ ...statut, nomBanque: null, paysBanque: null });
    await chargerBanques(pays);
  };

  if (!statut) return <p className="attente">Chargement…</p>;

  // Etat de chaque etape : "faite" (validee), "courante" (a faire maintenant),
  // ou "" (a venir, affichee grisee). Les etapes 1 et 2 sont couplees : creer
  // son application Enable Banking et fournir ses identifiants ne se separent
  // que dans la tete de l utilisateur, pas cote backend.
  const etape1Faite = statut.configuree;
  const etape1Courante = !statut.configuree;
  const etape2Faite = statut.configuree;
  const etape2Courante = !statut.configuree;
  const etape3Faite = !!statut.nomBanque;
  const etape3Courante = statut.configuree && !statut.nomBanque;
  const etape4Faite = statut.connectee;
  const etape4Courante = !!statut.nomBanque && !statut.connectee;
  const cls = (faite: boolean, courante: boolean) =>
    "etape" + (faite ? " faite" : courante ? " courante" : "");

  return (
    <div>
      <h1 className="titre-page">Connexion bancaire</h1>

      {info && <p className="message-info">{info}</p>}
      {erreur && <p className="message-erreur">{erreur}</p>}

      <ol className="etapes">
        <li className={cls(etape1Faite, etape1Courante)}>
          <h2>Cree ton application Enable Banking</h2>
          {etape1Courante && (
            <div className="etape-corps">
              <p>
                Sur <a href="https://enablebanking.com" target="_blank" rel="noreferrer">
                enablebanking.com</a>, cree un compte puis, dans le Control Panel,
                <strong> API Applications → Add a new application</strong> :
              </p>
              <ul className="liste-conseils">
                <li>Environment : <strong>Production</strong></li>
                <li>Generation de cle : laisse l option par defaut, le fichier
                  <code>.pem</code> se telecharge a la creation — <strong>garde-le,
                  il n est telechargeable qu une fois</strong></li>
                <li>
                  Allowed redirect URLs : colle exactement
                  <code className="copiable" onClick={() => {
                    navigator.clipboard.writeText(redirectUrl);
                    setInfo("URL copiee.");
                  }} title="Cliquer pour copier">{redirectUrl}</code>
                  {redirectSuspecte && (
                    <p className="message-erreur">
                      Cette adresse n est joignable que depuis le poste de
                      developpement. Ta banque y renverrait ton telephone apres
                      authentification, et tu tomberais sur une page morte.
                      Renseigne <code>TLPRICE_REDIRECT_URL</code> dans le
                      docker-compose.yml du serveur, puis relance le conteneur.
                    </p>
                  )}
                </li>
                <li>Privacy / Terms URL : l adresse de cette application suffit</li>
              </ul>
              <p className="avertissement">
                Etape a ne pas rater : une fois l application creee, clique
                <strong> « Activate by linking accounts » </strong> dans le Control
                Panel et lie ton compte bancaire. Sans ca, aucune donnee ne sera
                accessible et la connexion echouera a la fin.
              </p>
              <p>Quand c est fait, passe a l etape 2 juste en dessous.</p>
            </div>
          )}
        </li>

        <li className={cls(etape2Faite, etape2Courante)}>
          <h2>Renseigne tes identifiants</h2>
          {!statut.configuree && (
            <form onSubmit={envoyerConfig} className="etape-corps formulaire">
              <label>
                Application ID (affiche dans le Control Panel)
                <input
                  value={applicationId}
                  onChange={(e) => setApplicationId(e.target.value)}
                  placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                  required
                />
              </label>
              <label>
                Cle privee (le fichier .pem telecharge)
                <input
                  type="file"
                  accept=".pem,.key,.txt"
                  onChange={(e) => setFichierCle(e.target.files?.[0] ?? null)}
                  required
                />
              </label>
              <button className="bouton" disabled={envoiConfig || !fichierCle}>
                {envoiConfig ? "Verification…" : "Enregistrer"}
              </button>
            </form>
          )}
          {statut.configuree && (
            <p className="etape-resume">Application ID : {statut.applicationId}</p>
          )}
        </li>

        <li className={cls(etape3Faite, etape3Courante)}>
          <h2>Choisis ta banque</h2>
          {!statut.configuree && (
            <p className="etape-resume">D abord l etape 2.</p>
          )}
          {statut.configuree && !statut.nomBanque && (
            <div className="etape-corps">
              <div className="filtres-banques">
                <select value={pays} onChange={(e) => setPays(e.target.value)}>
                  {PAYS.map(([code, nom]) => (
                    <option key={code} value={code}>{nom}</option>
                  ))}
                </select>
                <input
                  placeholder="Rechercher ta banque…"
                  value={recherche}
                  onChange={(e) => setRecherche(e.target.value)}
                />
              </div>
              {chargementBanques ? (
                <p className="attente">Chargement des banques…</p>
              ) : (
                <ul className="liste-banques">
                  {banquesFiltrees.map((b) => (
                    <li key={b.nom + b.pays}>
                      <button className="carte-banque" onClick={() => choisir(b)}>
                        {b.logo && <img src={b.logo} alt="" />}
                        <span>{b.nom}</span>
                      </button>
                    </li>
                  ))}
                  {banquesFiltrees.length === 0 && (
                    <p className="vide">Aucune banque trouvee pour cette recherche.</p>
                  )}
                </ul>
              )}
            </div>
          )}
          {statut.nomBanque && (
            <p className="etape-resume">
              {statut.nomBanque} ({statut.paysBanque}){" "}
              <button className="bouton-texte" onClick={changerBanque}>
                Changer de banque
              </button>
            </p>
          )}
        </li>

        <li className={cls(etape4Faite, etape4Courante)}>
          <h2>Connecte ton compte</h2>
          {statut.connectee ? (
            <div className="etape-corps">
              <p className="etape-resume">
                Connecte. Consentement valable jusqu au{" "}
                {new Date(statut.validUntil!).toLocaleDateString("fr-FR")}.
              </p>
              <button className="bouton secondaire" onClick={connecter}>
                Renouveler la connexion
              </button>
            </div>
          ) : statut.nomBanque ? (
            <div className="etape-corps">
              <p>
                Tu vas etre redirige vers la page d authentification de ta banque
                (tes identifiants restent chez elle).
              </p>
              <button className="bouton" onClick={connecter}>
                Se connecter a {statut.nomBanque}
              </button>
            </div>
          ) : (
            <p className="etape-resume">D abord les etapes precedentes.</p>
          )}
        </li>
      </ol>
    </div>
  );
}
