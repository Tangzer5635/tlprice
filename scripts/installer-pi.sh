#!/usr/bin/env bash
# Installation / remise a niveau de tlprice sur un Raspberry Pi, en SSH.
#
#   git clone https://github.com/Tangzer5635/tlprice.git ~/tlprices
#   cd ~/tlprices && ./scripts/installer-pi.sh
#
# Idempotent : on peut le relancer sans risque. Il ne remplace jamais un
# .env existant et demande avant chaque changement systeme.
set -euo pipefail
cd "$(dirname "$0")/.."
DOSSIER=$(pwd)

demander() { printf '%s [o/N] ' "$1"; read -r r </dev/tty; [ "$r" = "o" ] || [ "$r" = "O" ]; }
titre() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

titre "Materiel"
ARCH=$(uname -m)
RAM_MO=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
MODELE=$(tr -d '\0' < /proc/device-tree/model 2>/dev/null || echo "inconnu")
echo "Modele : $MODELE"
echo "Architecture : $ARCH, RAM : ${RAM_MO} Mo"
case "$ARCH" in
  aarch64|arm64) echo "OK : systeme 64 bits." ;;
  armv7l)
    echo "Systeme 32 bits. L image tlprice est arm64 : installe Raspberry Pi OS Lite (64-bit)"
    echo "avec Raspberry Pi Imager. Un Pi 3B+ le supporte parfaitement."
    exit 1 ;;
  armv6l)
    echo "Ce Pi (ARMv6 : Pi 1 / Zero premiere generation) ne peut pas faire tourner"
    echo "Java 21 + PostgreSQL. Il faut au minimum un Pi 3B+ (ou 4 / 5)."
    exit 1 ;;
  *) echo "Architecture inattendue, on continue quand meme." ;;
esac

titre "Docker"
if ! command -v docker >/dev/null; then
  if demander "Docker n est pas installe. L installer (script officiel get.docker.com) ?"; then
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
    echo "Docker installe. DECONNECTE-TOI puis reconnecte-toi en SSH (groupe docker),"
    echo "et relance ce script."
    exit 0
  else
    exit 1
  fi
fi
docker compose version >/dev/null || { echo "Le plugin 'docker compose' manque."; exit 1; }
docker ps >/dev/null 2>&1 || { echo "Ton utilisateur n a pas acces a Docker : sudo usermod -aG docker $USER, puis reconnecte-toi."; exit 1; }
echo "OK : $(docker --version)"

titre "Swap"
# 1 Go de RAM pour Java + PostgreSQL : un swap de 1 Go evite l OOM killer
# lors des pics (demarrage de Spring, export Excel).
if [ "$RAM_MO" -lt 2000 ] && [ -f /etc/dphys-swapfile ]; then
  ACTUEL=$(grep -E '^CONF_SWAPSIZE=' /etc/dphys-swapfile | cut -d= -f2 || echo 0)
  if [ "${ACTUEL:-0}" -lt 1024 ]; then
    if demander "Swap actuel : ${ACTUEL:-0} Mo. Passer a 1024 Mo ?"; then
      sudo sed -i 's/^#\?CONF_SWAPSIZE=.*/CONF_SWAPSIZE=1024/' /etc/dphys-swapfile
      sudo dphys-swapfile setup && sudo dphys-swapfile swapon
    fi
  else
    echo "OK : swap de ${ACTUEL} Mo."
  fi
else
  echo "Rien a faire."
fi

titre "Fichier .env"
if [ -f .env ]; then
  echo "Un .env existe deja : on n y touche pas."
  bash scripts/preparer-env-file.sh | sed -n '1,12p' || true
else
  printf "Domaine public de l app (ex. tlprices.mondomaine.com) : "
  read -r DOMAINE </dev/tty
  printf "Email de contact (pour les notifications push) : "
  read -r CONTACT </dev/tty

  echo "Generation des cles VAPID (via un conteneur Node jetable)..."
  VAPID=$(docker run --rm node:22-alpine npx -y web-push generate-vapid-keys --json)
  PUB=$(printf '%s' "$VAPID" | sed -E 's/.*"publicKey":"([^"]+)".*/\1/')
  PRIV=$(printf '%s' "$VAPID" | sed -E 's/.*"privateKey":"([^"]+)".*/\1/')

  umask 077
  cat > .env <<ENV
# Genere par installer-pi.sh le $(date +%F). A SAUVEGARDER hors du Pi.
TLPRICE_CLE_CHIFFREMENT=$(openssl rand -base64 32)
TLPRICE_REDIRECT_URL=https://${DOMAINE}/banque/callback
TLPRICE_CONNEXION_DUREE_HEURES=720
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
TLPRICE_VAPID_CLE_PUBLIQUE=${PUB}
TLPRICE_VAPID_CLE_PRIVEE=${PRIV}
TLPRICE_VAPID_SUJET=mailto:${CONTACT}
TLPRICE_INSCRIPTION_OUVERTE=false
TLPRICE_SAUVEGARDES=${HOME}/tlprice-sauvegardes
ENV
  echo ".env cree (droits 600)."
fi

titre "Sauvegarde quotidienne (cron)"
LIGNE="45 3 * * * cd $DOSSIER && ./scripts/sauvegarde-pg.sh >> \$HOME/tlprice-sauvegarde.log 2>&1"
if crontab -l 2>/dev/null | grep -q "scripts/sauvegarde-pg.sh"; then
  echo "OK : deja planifiee."
elif demander "Planifier la sauvegarde PostgreSQL chaque nuit a 3h45 ?"; then
  (crontab -l 2>/dev/null; echo "$LIGNE") | crontab -
  echo "Planifiee. Journal : ~/tlprice-sauvegarde.log"
fi

titre "Demarrage"
./scripts/deployer.sh

titre "Verification"
bash scripts/verifier-prod.sh || true

cat <<FIN

Termine. Etapes restantes (voir docs/INSTALLATION-PI.md) :
  - exposer l app en HTTPS via Cloudflare Tunnel vers http://localhost:8082 ;
  - sur l iPhone : Safari > ton domaine > Partager > Sur l ecran d accueil ;
  - copier le .env et ~/tlprice-sauvegardes hors du Pi de temps en temps.
FIN
