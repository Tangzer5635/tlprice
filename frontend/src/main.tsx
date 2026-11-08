import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
// Polices servies par le Pi lui-meme : plus aucun appel a Google Fonts
// (vie privee, CSP stricte) et elles restent disponibles hors ligne.
import "@fontsource-variable/archivo/wdth.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "@fontsource/ibm-plex-mono/600.css";
import "./styles.css";
import { enregistrerServiceWorker } from "./push";

// Enregistre le service worker des le demarrage : sans lui, aucun push
// n arrive, et sur iPhone c est la seule voie possible.
enregistrerServiceWorker();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
