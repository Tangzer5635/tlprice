import { api } from "./api";

/**
 * Web Push cote navigateur.
 *
 * Le cas iPhone dicte tout le reste : depuis iOS 16.4, Safari sait recevoir
 * des notifications push, mais seulement si l app a ete ajoutee a l ecran
 * d accueil. Dans un onglet Safari classique, `PushManager` n existe meme
 * pas. D ou `estInstallee()`, qu on utilise pour expliquer l etape a faire
 * au lieu d afficher un bouton qui echouerait.
 */

/** La cle VAPID arrive en base64url ; l API la veut en octets. */
function base64UrlVersOctets(base64: string): Uint8Array {
  const bourrage = "=".repeat((4 - (base64.length % 4)) % 4);
  const normalise = (base64 + bourrage).replace(/-/g, "+").replace(/_/g, "/");
  const brut = atob(normalise);
  const octets = new Uint8Array(brut.length);
  for (let i = 0; i < brut.length; i++) octets[i] = brut.charCodeAt(i);
  return octets;
}

function versBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return "";
  const octets = new Uint8Array(buffer);
  let binaire = "";
  for (let i = 0; i < octets.length; i++) binaire += String.fromCharCode(octets[i]);
  return btoa(binaire).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export const pushSupporte = (): boolean =>
  "serviceWorker" in navigator &&
  "PushManager" in window &&
  typeof Notification !== "undefined";

/** L app tourne-t-elle depuis l ecran d accueil (et pas dans un onglet) ? */
export const estInstallee = (): boolean =>
  window.matchMedia("(display-mode: standalone)").matches ||
  (navigator as unknown as { standalone?: boolean }).standalone === true;

export const estIOS = (): boolean =>
  /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

/**
 * Nom lisible de l appareil.
 *
 * L ancienne version ne renvoyait que le systeme : trois reabonnements du
 * meme telephone donnaient trois lignes "iPhone" indiscernables. On ajoute
 * le navigateur, et surtout le mode d installation — sur iOS, l app posee
 * sur l ecran d accueil et le meme site ouvert dans Safari sont deux
 * abonnements distincts qui recevront chacun la notification.
 *
 * Ca ne suffit pas toujours a distinguer deux abonnements du meme appareil :
 * la date d ajout, affichee dans les reglages, tranche les cas restants, et
 * le nom reste modifiable a la main.
 */
function nommerAppareil(): string {
  const ua = navigator.userAgent;

  let systeme = "Navigateur";
  if (/iPhone/.test(ua)) systeme = "iPhone";
  else if (/iPad/.test(ua)) systeme = "iPad";
  else if (/Android/.test(ua)) systeme = "Android";
  else if (/Macintosh/.test(ua)) systeme = "Mac";
  else if (/Windows/.test(ua)) systeme = "Windows";
  else if (/Linux/.test(ua)) systeme = "Linux";

  let navigateur = "";
  if (/Edg\//.test(ua)) navigateur = "Edge";
  else if (/OPR\//.test(ua)) navigateur = "Opera";
  else if (/Firefox\//.test(ua)) navigateur = "Firefox";
  else if (/Chrome\//.test(ua)) navigateur = "Chrome";
  else if (/Safari\//.test(ua)) navigateur = "Safari";

  // display-mode: standalone = lance depuis l ecran d accueil.
  const installee = window.matchMedia
    && window.matchMedia("(display-mode: standalone)").matches;

  const morceaux = [systeme];
  if (installee) morceaux.push("app installee");
  else if (navigateur) morceaux.push(navigateur);
  return morceaux.join(" · ").slice(0, 120);
}

export async function enregistrerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) return null;
  try {
    return await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  } catch {
    return null;
  }
}

/** Abonnement deja actif sur cet appareil ? */
export async function abonnementActuel(): Promise<PushSubscription | null> {
  if (!pushSupporte()) return null;
  try {
    const enregistrement = await navigator.serviceWorker.ready;
    return await enregistrement.pushManager.getSubscription();
  } catch {
    return null;
  }
}

/**
 * Demande l autorisation puis abonne l appareil.
 * A appeler DEPUIS un clic : iOS refuse `requestPermission()` autrement.
 * Renvoie un message a afficher, ou null si tout s est bien passe.
 */
export async function activerPush(): Promise<string | null> {
  if (!pushSupporte()) {
    return estIOS() && !estInstallee()
      ? "Sur iPhone, ajoute d abord tlprice a l ecran d accueil (Partager → Sur l ecran d accueil), puis rouvre l app depuis cette icone."
      : "Ce navigateur ne gere pas les notifications push.";
  }

  const cle = await api.get("/api/push/cle-publique");
  if (!cle.disponible || !cle.cle) {
    return "Le serveur n a pas de cles VAPID configurees.";
  }

  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    return permission === "denied"
      ? "Notifications refusees. Reactive-les dans Reglages → tlprice → Notifications."
      : "Autorisation non accordee.";
  }

  await enregistrerServiceWorker();
  const enregistrement = await navigator.serviceWorker.ready;

  let abonnement = await enregistrement.pushManager.getSubscription();
  if (!abonnement) {
    abonnement = await enregistrement.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlVersOctets(cle.cle) as BufferSource,
    });
  }

  await api.post("/api/push/abonnement", {
    endpoint: abonnement.endpoint,
    cleP256dh: versBase64Url(abonnement.getKey("p256dh")),
    cleAuth: versBase64Url(abonnement.getKey("auth")),
    appareil: nommerAppareil(),
  });

  return null;
}

/** Desabonne cet appareil (les autres continuent de recevoir). */
export async function desactiverPush(): Promise<void> {
  const abonnement = await abonnementActuel();
  if (!abonnement) return;
  const endpoint = abonnement.endpoint;
  try {
    await abonnement.unsubscribe();
  } finally {
    await api.supprimer("/api/push/abonnement", { endpoint });
  }
}
