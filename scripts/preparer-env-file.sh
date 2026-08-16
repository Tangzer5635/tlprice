#!/usr/bin/env bash
# Prepare le .env pour un passage a "env_file:", qui transmet TOUT le fichier
# au conteneur au lieu d enumerer chaque variable a la main.
#
# A lancer SUR LE PI, dans le dossier du compose (~/tlprices) :
#   bash preparer-env-file.sh
#
# Ne modifie rien sans confirmation. Il verifie surtout une chose : "env_file"
# est plus strict que la substitution de compose, et refuse toute ligne qui
# n est pas exactement CLE=valeur. Une sortie de "web-push generate-vapid-keys"
# collee telle quelle dans le .env passe aujourd hui inapercue, mais ferait
# echouer le demarrage.
set -uo pipefail

[ -f .env ] || { echo "Aucun .env ici. Place-toi dans ~/tlprices."; exit 1; }

echo "== Analyse de .env =="
MAUVAISES=0
LIGNE=0
while IFS= read -r l || [ -n "$l" ]; do
  LIGNE=$((LIGNE+1))
  # Vide ou commentaire : sans importance
  case "$l" in ''|\#*) continue ;; esac
  if ! printf '%s' "$l" | grep -qE '^[A-Za-z_][A-Za-z0-9_]*='; then
    printf '  ligne %-3s refusee par env_file : %s\n' "$LIGNE" "$l"
    MAUVAISES=$((MAUVAISES+1))
  fi
done < .env

echo
if [ "$MAUVAISES" -eq 0 ]; then
  echo "Aucune ligne problematique. Tu peux passer a env_file sans rien changer."
else
  echo "$MAUVAISES ligne(s) a corriger avant de passer a env_file."
  echo
  echo "Ce sont probablement les cles VAPID collees depuis la sortie de"
  echo "web-push. Elles doivent prendre la forme :"
  echo
  echo "  TLPRICE_VAPID_CLE_PUBLIQUE=BBDK9Ci..."
  echo "  TLPRICE_VAPID_CLE_PRIVEE=NsWfWU6..."
  echo
  echo "Les valeurs actuellement utilisees sont celles ecrites en dur dans"
  echo "docker-compose.yml : recopie-les, ne les regenere pas, sinon tous tes"
  echo "appareils devront se reabonner."
  echo
  echo "Verification, une fois corrige :"
  echo "  bash preparer-env-file.sh"
fi

echo
echo "== Variables presentes =="
grep -E '^[A-Za-z_][A-Za-z0-9_]*=' .env | cut -d= -f1 | sort | sed 's/^/  /'

echo
echo "== Rappel de l edition a faire dans docker-compose.yml =="
cat <<'YAML'
  app:
    image: tangzeer/tlprice:latest
    restart: unless-stopped
    env_file: .env          <-- la ligne qui regle le probleme pour de bon
    labels:
      autoheal: "true"      <-- pour que le healthcheck serve a quelque chose
    environment:
      SPRING_PROFILES_ACTIVE: prod
      ...                    (le reste peut disparaitre, env_file s en charge,
                              sauf ce qui n est PAS dans .env)
YAML
