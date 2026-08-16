import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
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
