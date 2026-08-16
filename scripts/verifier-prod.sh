#!/usr/bin/env bash
# Diagnostic a lancer SUR LE PI, dans le dossier du compose (ex : ~/tlprices).
#   bash verifier-prod.sh
# Ne modifie rien : il lit et il rapporte.
set -uo pipefail

SERVICE="${SERVICE:-app}"
ok()   { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
ko()   { printf '  \033[31mKO\033[0m    %s\n' "$1"; PROBLEMES=$((PROBLEMES+1)); }
info() { printf '        %s\n' "$1"; }
PROBLEMES=0

echo "== Conteneurs =="
docker compose ps --format '  {{.Service}}\t{{.Status}}' 2>/dev/null || {
  echo "  Impossible de lire docker compose. Es-tu dans le bon dossier ?"; exit 1; }

echo
echo "== Version deployee =="
VERSION=$(docker compose logs "$SERVICE" 2>/dev/null \
  | grep -o 'Starting TlpriceApplication v[0-9.]*' | tail -1)
if [ -n "$VERSION" ]; then
  info "$VERSION"
  case "$VERSION" in *v4.*) ok "v4 en ligne" ;; *) ko "ce n est pas la v4 : l ancienne image tourne encore" ;; esac
else
  info "Version introuvable dans les logs (ils ont peut-etre ete tronques)."
fi

echo
echo "== Sante =="
ETAT=$(docker inspect --format '{{.State.Health.Status}}' \
  "$(docker compose ps -q "$SERVICE")" 2>/dev/null || echo "absent")
case "$ETAT" in
  healthy)  ok "healthcheck : healthy" ;;
  starting) info "healthcheck : starting (normal pendant ~3 min apres demarrage)" ;;
  absent)   ko "aucun healthcheck : l image deployee est anterieure a la v3" ;;
  *)        ko "healthcheck : $ETAT" ;;
esac
docker compose ps --format '{{.Service}}' | grep -q '^autoheal$' \
  && ok "autoheal present" \
  || ko "autoheal absent : un conteneur fige ne sera pas redemarre (voir A_LIRE)"

echo
echo "== Variables d environnement =="
lire() { docker compose exec -T "$SERVICE" printenv "$1" 2>/dev/null | tr -d '\r'; }

URL=$(lire TLPRICE_REDIRECT_URL)
if [ -z "$URL" ]; then
  ko "TLPRICE_REDIRECT_URL absente : la connexion bancaire refusera de demarrer"
  info "Ajoute-la sous environment: dans le docker-compose.yml, puis relance."
elif printf '%s' "$URL" | grep -qiE '//(localhost|127\.0\.0\.1)'; then
  ko "TLPRICE_REDIRECT_URL pointe vers $URL"
  info "C est la cause du renvoi vers localhost apres authentification bancaire."
else
  ok "TLPRICE_REDIRECT_URL = $URL"
fi

for V in TLPRICE_CLE_CHIFFREMENT TLPRICE_VAPID_CLE_PUBLIQUE TLPRICE_VAPID_CLE_PRIVEE; do
  [ -n "$(lire "$V")" ] && ok "$V renseignee" || ko "$V absente"
done

D=$(lire TLPRICE_CONNEXION_DUREE_HEURES)
info "Duree de connexion : ${D:-48 (defaut)} h"

echo
echo "== Sauvegardes automatiques =="
NB=$(docker compose exec -T "$SERVICE" sh -c 'ls -1 /app/data/sauvegardes/*.json 2>/dev/null | wc -l' | tr -d '\r ')
if [ "${NB:-0}" -gt 0 ]; then
  ok "$NB sauvegarde(s) presente(s)"
  docker compose exec -T "$SERVICE" sh -c 'ls -lt /app/data/sauvegardes/*.json | head -3' 2>/dev/null | sed 's/^/        /'
else
  ko "aucune sauvegarde : declenche-en une depuis Reglages pour verifier"
fi

echo
if [ "$PROBLEMES" -eq 0 ]; then
  printf '\033[32mTout est conforme.\033[0m\n'
else
  printf '\033[31m%s point(s) a corriger.\033[0m\n' "$PROBLEMES"
fi
exit 0
