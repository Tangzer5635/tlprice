/* tlprice — service worker.
 *
 * Deux roles :
 *
 * 1. Notifications push. Sur iPhone, ce fichier est indispensable : Safari
 *    ne delivre les notifications qu a une app ajoutee a l ecran d accueil,
 *    et uniquement via un service worker. Chaque push DOIT afficher une
 *    notification, sinon iOS finit par revoquer l autorisation.
 *
 * 2. Hors ligne. Le serveur est un Raspberry Pi a la maison : coupure de
 *    courant, box qui redemarre, metro sans reseau. Sans cache, l app
 *    installee affichait alors un ecran blanc ou la page d erreur de
 *    Cloudflare. Desormais :
 *      - l application elle-meme (HTML, JS, CSS, polices) est en cache ;
 *      - les dernieres reponses GET de l API sont gardees, et resservies si
 *        le serveur ne repond pas — l app les marque comme « hors ligne ».
 *    Les ecritures (POST, PUT...) ne sont jamais mises en cache ni rejouees.
 *
 * VERSION est remplace a chaque build (voir vite.config.ts) : un octet
 * different suffit a ce qu iOS installe le nouveau service worker.
 */

const VERSION = "__VERSION__";
const CACHE_APP = "tlprice-app-" + VERSION;
const CACHE_API = "tlprice-api";

/** Reponses API a ne jamais garder : fichiers, secrets, actions. */
const API_EXCLUES = [
  "/api/export",
  "/api/sauvegarde",
  "/api/banque/callback",
  "/api/push/cle-publique",
  "/api/auth/config",
  "/api/auth/login",
  "/api/auth/logout",
];

/** Cloudflare repond 502/530 quand le Pi est eteint : c est un « hors ligne ». */
const serveurAbsent = (reponse) => reponse.status >= 500;

async function precacher() {
  const cache = await caches.open(CACHE_APP);
  const reponse = await fetch("/", { cache: "no-store", credentials: "same-origin" });
  if (!reponse.ok) return;
  const html = await reponse.clone().text();
  await cache.put("/", reponse);
  // Les fichiers de /assets references par index.html (JS et CSS).
  const fichiers = new Set(
    Array.from(html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g), (m) => m[1])
  );
  for (const f of ["/theme.js", "/manifest.webmanifest", "/icone-192.png", "/favicon.svg"]) {
    fichiers.add(f);
  }
  await Promise.all(
    Array.from(fichiers).map((url) => cache.add(url).catch(() => undefined))
  );
}

self.addEventListener("install", (evenement) => {
  evenement.waitUntil(precacher().catch(() => undefined).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (evenement) => {
  evenement.waitUntil(
    caches
      .keys()
      .then((noms) =>
        Promise.all(
          noms
            .filter((n) => n.startsWith("tlprice-app-") && n !== CACHE_APP)
            .map((n) => caches.delete(n))
        )
      )
      .then(() => self.clients.claim())
  );
});

/** Messages de la page : vider les donnees a la deconnexion. */
self.addEventListener("message", (evenement) => {
  if (evenement.data && evenement.data.type === "vider-donnees") {
    evenement.waitUntil(caches.delete(CACHE_API));
  }
});

/** Reponse issue du cache, marquee pour que l app sache qu elle est perimee. */
async function depuisCache(requete) {
  const cache = await caches.open(CACHE_API);
  const enCache = await cache.match(requete);
  if (!enCache) return null;
  const entetes = new Headers(enCache.headers);
  entetes.set("X-Tlprice-Hors-Ligne", enCache.headers.get("Date") || "1");
  return new Response(await enCache.blob(), {
    status: enCache.status,
    statusText: enCache.statusText,
    headers: entetes,
  });
}

async function api(requete) {
  try {
    const reponse = await fetch(requete);
    if (reponse.status === 401) {
      // Session expiree ou deconnexion : plus rien a resservir.
      await caches.delete(CACHE_API);
      return reponse;
    }
    if (serveurAbsent(reponse)) {
      return (await depuisCache(requete)) || reponse;
    }
    if (reponse.ok) {
      const cache = await caches.open(CACHE_API);
      await cache.put(requete, reponse.clone());
    }
    return reponse;
  } catch (e) {
    const enCache = await depuisCache(requete);
    if (enCache) return enCache;
    throw e;
  }
}

async function navigation(requete) {
  try {
    const reponse = await fetch(requete);
    if (serveurAbsent(reponse)) {
      return (await caches.match("/", { cacheName: CACHE_APP })) || reponse;
    }
    return reponse;
  } catch (e) {
    const coquille = await caches.match("/", { cacheName: CACHE_APP });
    if (coquille) return coquille;
    throw e;
  }
}

/** Fichiers immuables (nom hashe par Vite) : cache d abord. */
async function fichierStatique(requete) {
  const enCache = await caches.match(requete);
  if (enCache) return enCache;
  const reponse = await fetch(requete);
  if (reponse.ok) {
    const cache = await caches.open(CACHE_APP);
    await cache.put(requete, reponse.clone());
  }
  return reponse;
}

/** Autres fichiers de la racine (theme.js, icones) : reseau d abord. */
async function fichierRacine(requete) {
  try {
    const reponse = await fetch(requete);
    if (!serveurAbsent(reponse)) return reponse;
  } catch (e) {
    /* hors ligne : on tente le cache */
  }
  const enCache = await caches.match(requete);
  if (enCache) return enCache;
  return fetch(requete);
}

self.addEventListener("fetch", (evenement) => {
  const requete = evenement.request;
  if (requete.method !== "GET") return;
  const url = new URL(requete.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith("/api/")) {
    if (API_EXCLUES.some((p) => url.pathname.startsWith(p))) return;
    evenement.respondWith(api(requete));
  } else if (url.pathname.startsWith("/actuator/")) {
    return;
  } else if (requete.mode === "navigate") {
    evenement.respondWith(navigation(requete));
  } else if (url.pathname.startsWith("/assets/")) {
    evenement.respondWith(fichierStatique(requete));
  } else {
    evenement.respondWith(fichierRacine(requete));
  }
});

/* ------------------------------------------------------------------ */
/* Notifications push                                                  */
/* ------------------------------------------------------------------ */

self.addEventListener("push", (evenement) => {
  let donnees = { titre: "tlprice", corps: "", url: "/" };
  if (evenement.data) {
    try {
      donnees = Object.assign(donnees, evenement.data.json());
    } catch (e) {
      donnees.corps = evenement.data.text();
    }
  }

  evenement.waitUntil(
    self.registration.showNotification(donnees.titre || "tlprice", {
      body: donnees.corps || "",
      icon: "/icone-192.png",
      badge: "/icone-192.png",
      /* Un seul rappel visible a la fois : le nouveau remplace l ancien. */
      tag: "tlprice-jour",
      renotify: true,
      data: { url: donnees.url || "/" },
    })
  );
});

self.addEventListener("notificationclick", (evenement) => {
  evenement.notification.close();
  const cible = (evenement.notification.data && evenement.notification.data.url) || "/";

  evenement.waitUntil(
    self.clients
      .matchAll({ type: "window", includeUncontrolled: true })
      .then((fenetres) => {
        for (const fenetre of fenetres) {
          if ("focus" in fenetre) {
            fenetre.navigate && fenetre.navigate(cible);
            return fenetre.focus();
          }
        }
        return self.clients.openWindow(cible);
      })
  );
});
