#!/usr/bin/env bash
# Sauvegarde complete de tlprice, a lancer SUR LE PI (cron quotidien).
#
#   ./scripts/sauvegarde-pg.sh
#
# Ce que ca sauvegarde, hors des volumes Docker :
#   1. un dump PostgreSQL complet (.sql.gz) — c est LA sauvegarde : elle
#      contient tout, y compris les connexions bancaires chiffrees ;
#   2. les exports JSON nocturnes de l app (copie de /app/data/sauvegardes) ;
#   3. le fichier .env, uniquement s il a change. Sans TLPRICE_CLE_CHIFFREMENT,
#      les secrets du dump sont illisibles : les deux vont ensemble.
#
# Pourquoi c est indispensable : jusqu ici, les seules sauvegardes vivaient
# dans un volume Docker, sur la MEME carte SD que la base. Une carte SD qui
# meurt (c est leur destin sur un Pi allume 24 h/24) emportait tout.
#
# Variables (optionnelles, dans .env ou l environnement) :
#   TLPRICE_SAUVEGARDES   dossier de destination (defaut : ~/tlprice-sauvegardes)
#                         -> idealement une cle USB / un disque monte, PAS la carte SD
#   TLPRICE_RETENTION     jours de dumps conserves (defaut : 30)
#   TLPRICE_RCLONE_DEST   ex. "gdrive:tlprice" : copie hors site via rclone
set -euo pipefail
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"

cd "$(dirname "$0")/.."
[ -f docker-compose.yml ] || { echo "docker-compose.yml introuvable dans $(pwd)"; exit 1; }

lire_env() { [ -f .env ] && grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true; }

DEST="${TLPRICE_SAUVEGARDES:-$(lire_env TLPRICE_SAUVEGARDES)}"
DEST="${DEST:-$HOME/tlprice-sauvegardes}"
RETENTION="${TLPRICE_RETENTION:-$(lire_env TLPRICE_RETENTION)}"
RETENTION="${RETENTION:-30}"
RCLONE_DEST="${TLPRICE_RCLONE_DEST:-$(lire_env TLPRICE_RCLONE_DEST)}"

umask 077
mkdir -p "$DEST/json" "$DEST/env"

HORODATAGE=$(date +%Y%m%d-%H%M%S)
CIBLE="$DEST/tlprice-$HORODATAGE.sql.gz"

echo "[$(date '+%F %T')] Dump PostgreSQL -> $CIBLE"
docker compose exec -T db pg_dump -U tlprice -d tlprice --clean --if-exists --no-owner \
  | gzip -9 > "$CIBLE.partiel"

# Un dump vide ou tronque est pire que pas de dump : il donne confiance.
gzip -t "$CIBLE.partiel"
if ! zcat "$CIBLE.partiel" | grep -q "PostgreSQL database dump complete"; then
  echo "ERREUR : dump incomplet, conserve sous $CIBLE.partiel pour analyse." >&2
  exit 1
fi
mv "$CIBLE.partiel" "$CIBLE"
echo "  $(du -h "$CIBLE" | cut -f1) ecrits."

echo "Copie des exports JSON de l app"
docker compose cp app:/app/data/sauvegardes/. "$DEST/json/" >/dev/null 2>&1 \
  || echo "  (aucun export JSON a copier)"

if [ -f .env ]; then
  DERNIER=$(ls -1t "$DEST"/env/env-* 2>/dev/null | head -1 || true)
  if [ -z "$DERNIER" ] || ! cmp -s .env "$DERNIER"; then
    cp .env "$DEST/env/env-$HORODATAGE"
    echo "Copie du .env (il a change)"
  fi
fi

echo "Purge des dumps de plus de $RETENTION jours"
find "$DEST" -maxdepth 1 -name 'tlprice-*.sql.gz' -mtime +"$RETENTION" -delete
find "$DEST" -maxdepth 1 -name 'tlprice-*.sql.gz.partiel' -mtime +2 -delete

if [ -n "$RCLONE_DEST" ]; then
  if command -v rclone >/dev/null; then
    echo "Copie hors site -> $RCLONE_DEST"
    rclone copy "$DEST" "$RCLONE_DEST" --exclude "env/**"
  else
    echo "TLPRICE_RCLONE_DEST defini mais rclone n est pas installe." >&2
  fi
fi

echo "[$(date '+%F %T')] Sauvegarde terminee."
