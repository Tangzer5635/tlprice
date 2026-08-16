#!/usr/bin/env bash
# Rotation des secrets, a lancer SUR LE PI dans le dossier du compose.
#   bash rotation-secrets.sh
#
# A faire apres l envoi accidentel du .env. Le script ne decide rien tout
# seul : il propose chaque rotation, explique ce qu elle coute, et attend
# une confirmation.
set -euo pipefail

[ -f .env ] || { echo "Aucun .env ici. Place-toi dans le dossier du compose."; exit 1; }

SAUVE=".env.avant-rotation-$(date +%Y%m%d-%H%M%S)"
cp .env "$SAUVE"
echo "Copie de securite : $SAUVE"
echo

demander() { printf '%s [o/N] ' "$1"; read -r r; [ "$r" = "o" ] || [ "$r" = "O" ]; }

remplacer() { # cle valeur
  if grep -q "^$1=" .env; then
    tmp=$(mktemp); sed "s|^$1=.*|$1=$2|" .env > "$tmp"; mv "$tmp" .env
  else
    printf '%s=%s\n' "$1" "$2" >> .env
  fi
}

# ---------- 1. Mot de passe PostgreSQL ----------
echo "1) Mot de passe PostgreSQL"
echo "   Cout : nul. Deux minutes, aucune donnee perdue."
if demander "   Le changer ?"; then
  NOUVEAU=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
  UTIL=$(grep -E '^DB_USER=' .env | cut -d= -f2-); UTIL=${UTIL:-tlprice}
  docker compose exec -T db psql -U "$UTIL" -d "${DB_NAME:-tlprice}" \
    -c "ALTER USER $UTIL WITH PASSWORD '$NOUVEAU';"
  remplacer DB_PASSWORD "$NOUVEAU"
  echo "   Fait."
fi
echo

# ---------- 2. Cles VAPID ----------
echo "2) Cles VAPID (notifications push)"
echo "   Cout : tous les appareils abonnes doivent se reabonner."
echo "   Sur l iPhone : Reglages > desactiver puis reactiver les notifications."
if demander "   Les changer ?"; then
  if ! command -v npx >/dev/null; then
    echo "   npx introuvable sur le Pi. Genere les cles sur ton PC avec :"
    echo "     npx web-push generate-vapid-keys"
    echo "   puis reporte-les a la main dans .env."
  else
    SORTIE=$(npx --yes web-push generate-vapid-keys)
    PUB=$(printf '%s' "$SORTIE" | grep -A1 'Public Key' | tail -1 | tr -d ' \r')
    PRIV=$(printf '%s' "$SORTIE" | grep -A1 'Private Key' | tail -1 | tr -d ' \r')
    remplacer TLPRICE_VAPID_CLE_PUBLIQUE "$PUB"
    remplacer TLPRICE_VAPID_CLE_PRIVEE "$PRIV"
    echo "   Fait. Pense a te reabonner sur chaque appareil."
  fi
fi
echo

# ---------- 3. Cle de chiffrement ----------
echo "3) Cle de chiffrement AES (TLPRICE_CLE_CHIFFREMENT)"
echo "   ATTENTION, c est la rotation couteuse :"
echo "     - la connexion Enable Banking devra etre refaite (.pem a reenvoyer) ;"
echo "     - la configuration SMTP stockee en base sera illisible, a ressaisir ;"
echo "     - la connexion de 48h est invalidee, un login sera redemande."
echo "   Prevois un quart d heure devant toi."
if demander "   La changer MAINTENANT ?"; then
  remplacer TLPRICE_CLE_CHIFFREMENT "$(openssl rand -base64 32)"
  echo "   Fait. Reconnecte ta banque des le redemarrage."
fi
echo

echo "Redemarrage pour appliquer :"
echo "  docker compose up -d"
echo
echo "Si quelque chose tourne mal : cp $SAUVE .env && docker compose up -d"
