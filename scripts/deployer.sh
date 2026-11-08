#!/usr/bin/env bash
# Met a jour tlprice SUR LE PI :
#
#   ./scripts/deployer.sh
#
# 1. verifie que les volumes de donnees existants seront bien reutilises ;
# 2. sauvegarde la base AVANT de toucher a quoi que ce soit ;
# 3. telecharge la nouvelle image (construite par GitHub Actions) ;
# 4. redemarre et attend que l app soit reellement saine ;
# 5. fait le menage des anciennes images (la carte SD n est pas extensible).
set -euo pipefail
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "Pas de .env ici : lance d abord scripts/installer-pi.sh"; exit 1; }

PREFIXE=$(grep -E '^TLPRICE_VOLUME_PREFIXE=' .env | tail -1 | cut -d= -f2- || true)
PREFIXE=${PREFIXE:-tlprices}
VOLUME_DB="${PREFIXE}_donnees_db"

# Garde-fou : si le volume de base n existe pas mais qu un AUTRE volume
# *_donnees_db existe, c est presque toujours une erreur de prefixe — et on
# s appreterait a demarrer sur une base vide.
if ! docker volume inspect "$VOLUME_DB" >/dev/null 2>&1; then
  AUTRES=$(docker volume ls -q | grep '_donnees_db$' || true)
  if [ -n "$AUTRES" ]; then
    echo "ATTENTION : le volume $VOLUME_DB n existe pas, mais ceux-ci existent :"
    echo "$AUTRES" | sed 's/^/    /'
    echo "Ajoute dans .env la ligne TLPRICE_VOLUME_PREFIXE=<prefixe> correspondant"
    echo "(ex. pour 'tlprice_donnees_db' : TLPRICE_VOLUME_PREFIXE=tlprice), puis relance."
    exit 1
  fi
  echo "Premiere installation : la base $VOLUME_DB va etre creee."
else
  echo "== Sauvegarde avant mise a jour =="
  if docker compose ps --status running -q db 2>/dev/null | grep -q .; then
    ./scripts/sauvegarde-pg.sh | tail -2
  else
    echo "   (base arretee : pas de sauvegarde possible, on continue)"
  fi
fi

echo "== Telechargement de l image =="
docker compose pull

echo "== Redemarrage =="
docker compose up -d --remove-orphans

echo "== Attente de l etat healthy (jusqu a 5 min sur un Pi 3) =="
CONTENEUR=$(docker compose ps -q app)
for i in $(seq 1 60); do
  ETAT=$(docker inspect --format '{{.State.Health.Status}}' "$CONTENEUR" 2>/dev/null || echo "?")
  case "$ETAT" in
    healthy) echo "   OK apres $((i * 5)) s"; break ;;
    unhealthy) echo "   ECHEC : conteneur unhealthy"; docker compose logs --tail 60 app; exit 1 ;;
  esac
  sleep 5
done
[ "$ETAT" = "healthy" ] || { echo "   Toujours pas healthy. Logs :"; docker compose logs --tail 60 app; exit 1; }

docker compose logs app 2>/dev/null | grep -o 'Starting TlpriceApplication v[0-9.]*' | tail -1 || true

echo "== Menage des anciennes images =="
docker image prune -f >/dev/null && echo "   fait."
