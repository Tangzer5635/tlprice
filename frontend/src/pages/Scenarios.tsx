import { useEffect, useState } from "react";
import { api, CATEGORIES, euros, LIBELLES_CATEGORIES, dateFr, formatLocal } from "../api";

type Ligne = {
  id?: number;
  libelle: string;
  montant: string;
  type: "DEBIT" | "CREDIT";
  categorie: string;
  date: string;
};

type Scenario = {
  id: number;
  nom: string;
  lignes: {
    id: number;
    libelle: string;
    montant: number;
    type: "DEBIT" | "CREDIT";
    categorie: string;
    date: string;
  }[];
};

const ligneVide = (): Ligne => ({
  libelle: "",
  montant: "",
  type: "DEBIT",
  categorie: "AUTRE",
  date: formatLocal(new Date()),
});

export default function Scenarios() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [nom, setNom] = useState("");
  const [lignes, setLignes] = useState<Ligne[]>([ligneVide()]);
  const [edition, setEdition] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");
  const [erreurChargement, setErreurChargement] = useState("");

  const charger = () =>
    api
      .get("/api/scenarios")
      .then((l) => {
        setScenarios(l);
        setErreurChargement("");
      })
      .catch((e) => setErreurChargement(e.message));

  useEffect(() => {
    charger();
  }, []);

  const reinitialiser = () => {
    setNom("");
    setLignes([ligneVide()]);
    setEdition(null);
  };

  const majLigne = (i: number, champ: keyof Ligne, valeur: string) => {
    setLignes((l) => l.map((x, j) => (j === i ? { ...x, [champ]: valeur } : x)));
  };

  const enregistrer = async () => {
    setErreur("");
    setMessage("");
    const utiles = lignes.filter((l) => l.libelle.trim() && l.montant.trim());
    if (!nom.trim()) {
      setErreur("Donne un nom a ce scenario.");
      return;
    }
    if (utiles.length === 0) {
      setErreur("Ajoute au moins une ligne.");
      return;
    }
    const corps = {
      nom,
      lignes: utiles.map((l) => ({
        libelle: l.libelle,
        montant: Number(l.montant.replace(",", ".")),
        type: l.type,
        categorie: l.categorie,
        date: l.date,
      })),
    };
    try {
      if (edition === null) await api.post("/api/scenarios", corps);
      else await api.put("/api/scenarios/" + edition, corps);
      reinitialiser();
      await charger();
      setMessage("Scenario enregistre. Selectionne-le sur l ecran Solde pour voir son effet.");
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const reprendre = (s: Scenario) => {
    setEdition(s.id);
    setNom(s.nom);
    setLignes(
      s.lignes.map((l) => ({
        id: l.id,
        libelle: l.libelle,
        montant: String(l.montant),
        type: l.type,
        categorie: l.categorie,
        date: l.date,
      }))
    );
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const supprimer = async (id: number) => {
    try {
      await api.supprimer("/api/scenarios/" + id);
      if (edition === id) reinitialiser();
      await charger();
    } catch (e) {
      setErreur(e instanceof Error ? e.message : "Erreur");
    }
  };

  const effetDe = (s: Scenario) => {
    let net = 0;
    for (const l of s.lignes) net += l.type === "DEBIT" ? -l.montant : l.montant;
    return net;
  };

  return (
    <div>
      <h1 className="titre-page">Scenarios</h1>
      {erreurChargement && (
        <p className="message-erreur">
          Impossible de lire tes scenarios ({erreurChargement}). La creation
          peut quand meme fonctionner.
        </p>
      )}
      <p className="intro">
        « Et si j achete ca a 400 euros le 12 ? » Un scenario est une liste de
        mouvements hypothetiques qui n entrent jamais dans ta projection
        reelle.
      </p>

      <ol className="mode-emploi">
        <li>
          <strong>Nomme l hypothese</strong> — « Changer de voiture »,
          « Vacances aout ». C est ce nom qui apparaitra dans le selecteur.
        </li>
        <li>
          <strong>Ajoute les mouvements</strong> qu elle impliquerait : une
          depense de 400 € le 12, ou plusieurs lignes si l hypothese en a
          plusieurs (un apport, puis une mensualite chaque mois).
        </li>
        <li>
          <strong>Va sur l ecran Solde</strong> et choisis ce scenario dans le
          selecteur. Le solde projete, la courbe et la liste des mouvements
          l integrent, et le solde <em>sans</em> le scenario reste affiche
          juste en dessous avec l ecart entre les deux.
        </li>
      </ol>

      <p className="note">
        Rien n est ajoute a tes vraies operations : deselectionne le scenario
        et tout redevient comme avant.
      </p>

      <section className="carte-parametre">
        <h2 className="titre-section">
          {edition === null ? "Nouveau scenario" : "Modifier le scenario"}
        </h2>

        <label className="champ champ-large">
          <span>Nom</span>
          <input
            placeholder="Changer de voiture"
            value={nom}
            onChange={(e) => setNom(e.target.value)}
            maxLength={120}
          />
        </label>

        <div className="lignes-scenario">
          {lignes.map((l, i) => (
            <div key={i} className="formulaire-saisie">
              <label className="champ champ-large">
                <span>Libelle</span>
                <input
                  placeholder="Apport"
                  value={l.libelle}
                  onChange={(e) => majLigne(i, "libelle", e.target.value)}
                />
              </label>
              <label className="champ">
                <span>Montant</span>
                <input
                  placeholder="0,00"
                  inputMode="decimal"
                  value={l.montant}
                  onChange={(e) => majLigne(i, "montant", e.target.value)}
                />
              </label>
              <label className="champ">
                <span>Sens</span>
                <select value={l.type} onChange={(e) => majLigne(i, "type", e.target.value)}>
                  <option value="DEBIT">Depense</option>
                  <option value="CREDIT">Recette</option>
                </select>
              </label>
              <label className="champ">
                <span>Categorie</span>
                <select
                  value={l.categorie}
                  onChange={(e) => majLigne(i, "categorie", e.target.value)}
                >
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {LIBELLES_CATEGORIES[c]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="champ">
                <span>Date</span>
                <input
                  type="date"
                  value={l.date}
                  onChange={(e) => majLigne(i, "date", e.target.value)}
                />
              </label>
              {lignes.length > 1 && (
                <button
                  type="button"
                  className="bouton-texte danger"
                  onClick={() => setLignes((x) => x.filter((_, j) => j !== i))}
                >
                  Retirer
                </button>
              )}
            </div>
          ))}
        </div>

        <div className="hero-actions">
          <button
            type="button"
            className="bouton secondaire"
            onClick={() => setLignes((l) => [...l, ligneVide()])}
          >
            Ajouter une ligne
          </button>
          <button type="button" className="bouton" onClick={enregistrer}>
            {edition === null ? "Creer le scenario" : "Enregistrer"}
          </button>
          {edition !== null && (
            <button type="button" className="bouton-texte" onClick={reinitialiser}>
              Annuler
            </button>
          )}
        </div>

        {message && <p className="message-info">{message}</p>}
        {erreur && <p className="message-erreur">{erreur}</p>}
      </section>

      <h2 className="titre-section">Mes scenarios</h2>
      {scenarios.length === 0 ? (
        <p className="vide">
          Aucun scenario. Le premier est souvent le plus utile : l achat que tu
          hesites a faire ce mois-ci.
        </p>
      ) : (
        <ul className="liste-cartes">
          {scenarios.map((s) => {
            const net = effetDe(s);
            return (
              <li key={s.id} className="carte-scenario">
                <div className="controle-tete">
                  <span className="controle-libelle">{s.nom}</span>
                  <span className={"scenario-effet " + (net < 0 ? "texte-debit" : "texte-credit")}>
                    {net < 0 ? "−" : "+"} {euros(Math.abs(net))}
                  </span>
                </div>
                <ul className="liste-mouvements">
                  {s.lignes.map((l) => (
                    <li key={l.id} className="mouvement mouvement-jour">
                      <span
                        className={
                          "mouvement-barre " +
                          (l.type === "DEBIT" ? "barre-debit" : "barre-credit")
                        }
                      />
                      <span className="mouvement-libelle">
                        {l.libelle}
                        <em className="badge">{dateFr(l.date)}</em>
                      </span>
                      <span
                        className={
                          "mouvement-montant " +
                          (l.type === "DEBIT" ? "texte-debit" : "texte-credit")
                        }
                      >
                        {l.type === "DEBIT" ? "−" : "+"} {euros(l.montant)}
                      </span>
                    </li>
                  ))}
                </ul>
                <div className="controle-actions">
                  <button className="bouton-texte" onClick={() => reprendre(s)}>
                    Modifier
                  </button>
                  <button className="bouton-texte danger" onClick={() => supprimer(s.id)}>
                    Supprimer
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
