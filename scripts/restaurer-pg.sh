#!/usr/bin/env bash
# Restaure un dump produit par sauvegarde-pg.sh. A lancer SUR LE PI.
#
#   ./scripts/restaurer-pg.sh ~/tlprice-sauvegardes/tlprice-20261108-031500.sql.gz
#
# ECRASE la base actuelle. Une sauvegarde de l etat courant est faite avant,
# pour pouvoir revenir en arriere si on s est trompe de fichier.
#
# Rappel : les connexions bancaires et le SMTP sont chiffres avec
# TLPRICE_CLE_CHIFFREMENT. Restaurer sur une nouvelle machine demande donc
# le MEME .env (copie dans le dossier env/ des sauvegardes).
set -euo pipefail

cd "$(dirname "$0")/.."
DUMP="${1:-}"
[ -n "$DUMP" ] && [ -f "$DUMP" ] || { echo "Usage : $0 fichier.sql.gz"; exit 1; }
gzip -t "$DUMP" || { echo "Fichier corrompu : $DUMP"; exit 1; }

echo "La base actuelle va etre REMPLACEE par : $DUMP"
printf "Continuer ? [o/N] "; read -r r
[ "$r" = "o" ] || [ "$r" = "O" ] || { echo "Abandon."; exit 0; }

echo "1/4 Sauvegarde de securite de l etat actuel"
docker compose up -d db >/dev/null
./scripts/sauvegarde-pg.sh >/dev/null && echo "    faite."

echo "2/4 Arret de l application"
docker compose stop app

echo "3/4 Restauration"
zcat "$DUMP" | docker compose exec -T db psql -q -v ON_ERROR_STOP=1 -U tlprice -d tlprice >/dev/null

echo "4/4 Redemarrage"
docker compose up -d app
echo "Termine. L app redemarre (1 a 3 minutes sur un Pi)."
