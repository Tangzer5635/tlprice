#!/bin/sh
# Point d entree du conteneur tlprice.
#
# Les versions <= 4.2 tournaient en root : le volume /app/data (sauvegardes,
# cle auto-generee) leur appartient. On le rend a l utilisateur tlprice a
# chaque demarrage (quelques fichiers, instantane), puis on abandonne les
# droits root avant de lancer Java. Une faille dans l app ne donne plus
# la main en root dans le conteneur.
set -e

if [ "$(id -u)" = "0" ]; then
  mkdir -p /app/data/sauvegardes
  chown -R tlprice:tlprice /app/data
  exec setpriv --reuid=tlprice --regid=tlprice --init-groups \
    sh -c 'exec java $JAVA_OPTS -jar /app/app.jar "$@"' tlprice "$@"
fi

exec sh -c 'exec java $JAVA_OPTS -jar /app/app.jar "$@"' tlprice "$@"
