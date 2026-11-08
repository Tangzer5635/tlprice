import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import basicSsl from "@vitejs/plugin-basic-ssl";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Estampille dist/sw.js avec un identifiant de build. Sans ca, le service
 * worker est identique octet pour octet d un deploiement a l autre, iOS ne
 * l installe pas a nouveau, et les anciens fichiers restent en cache.
 */
function versionServiceWorker(): Plugin {
  let sortie = "dist";
  return {
    name: "tlprice-version-sw",
    apply: "build",
    configResolved(config) {
      sortie = resolve(config.root, config.build.outDir);
    },
    closeBundle() {
      const chemin = resolve(sortie, "sw.js");
      const version = new Date().toISOString().replace(/[^0-9]/g, "").slice(0, 14);
      writeFileSync(chemin, readFileSync(chemin, "utf8").replace("__VERSION__", version));
    },
  };
}

// HTTPS local (certificat auto-signe) car Enable Banking exige une
// redirect URL en HTTPS meme en developpement.
export default defineConfig({
  plugins: [react(), basicSsl(), versionServiceWorker()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});
