// Applique le theme AVANT le premier paint pour eviter le flash.
// Au premier lancement on suit la preference du systeme au lieu
// d imposer le theme sombre.
(function () {
  var t = null;
  try {
    t = localStorage.getItem("tlprice-theme");
  } catch (e) {
    /* stockage indisponible (navigation privee) */
  }
  if (!t) {
    t = window.matchMedia("(prefers-color-scheme: light)").matches ? "clair" : "sombre";
  }
  document.documentElement.setAttribute("data-theme", t);
  // La barre du navigateur suit le theme choisi dans l app.
  var couleur = t === "clair" ? "#ffffff" : "#0f1613";
  var balises = document.querySelectorAll('meta[name="theme-color"]');
  for (var i = 0; i < balises.length; i++) {
    balises[i].setAttribute("content", couleur);
  }
})();
