/* tlprice — service worker.
 *
 * Sur iPhone, ce fichier est indispensable : Safari ne delivre les
 * notifications qu a une app ajoutee a l ecran d accueil, et uniquement
 * via un service worker. Chaque push DOIT afficher une notification,
 * sinon iOS finit par revoquer l autorisation.
 */

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (evenement) => {
  evenement.waitUntil(self.clients.claim());
});

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
