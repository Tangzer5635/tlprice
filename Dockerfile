# syntax=docker/dockerfile:1

# Les deux etapes de build tournent sur l architecture de la MACHINE QUI
# CONSTRUIT ($BUILDPLATFORM), pas sur celle du Pi : le JAR et le bundle JS
# sont independants du processeur. Construire Maven sous emulation arm64
# prenait plus de 20 minutes ; en natif, deux.

# ---------- 1. Build du frontend ----------
# Node 22 : requis par Vite 8 (^20.19 || >=22.12).
FROM --platform=$BUILDPLATFORM node:22-alpine AS front
WORKDIR /front

COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# ---------- 2. Build du backend (avec le front dans static/) ----------
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS back
WORKDIR /back
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=front /front/dist ./src/main/resources/static
# Les tests tournent en CI (.github/workflows), pas ici : l image reste
# reproductible meme sans base de test.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests \
 && cp target/tlprice-*.jar /back/app.jar

# ---------- 3. Image finale (seule etape construite pour arm64) ----------
# Pas d alpine : eclipse-temurin:21-jre-alpine n existe pas pour arm64.
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.source="https://github.com/tangzer5635/tlprice" \
      org.opencontainers.image.description="tlprice - solde projete" \
      org.opencontainers.image.licenses="UNLICENSED"

WORKDIR /app

# curl : pour le HEALTHCHECK. tzdata est deja dans l image.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system tlprice \
 && useradd --system --gid tlprice --home-dir /app --shell /usr/sbin/nologin tlprice \
 && mkdir -p /app/data/sauvegardes

COPY --from=back /back/app.jar app.jar
COPY scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 755 /usr/local/bin/docker-entrypoint.sh

VOLUME /app/data
EXPOSE 8081

# Heure de Paris pour les logs et pour tout LocalDate.now() du code.
ENV TZ=Europe/Paris

# Raspberry Pi 3B+ : 1 Go partage avec PostgreSQL et le systeme.
#  -Xmx512m / MaxMetaspaceSize : borne la JVM pour eviter l OOM killer.
#  SerialGC : le moins gourmand, et le Pi n a de toute facon que 4 coeurs lents.
#  TieredStopAtLevel=1 : compilateur C1 seulement. Demarrage nettement plus
#    rapide et moins de memoire de code, pour une perte de debit invisible
#    sur une app a un utilisateur.
#  ExitOnOutOfMemoryError : une JVM a court de memoire meurt franchement au
#    lieu de survivre dans un etat incoherent ; Docker la redemarre.
ENV JAVA_OPTS="-Xmx512m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+ExitOnOutOfMemoryError"

# restart: unless-stopped ne couvre que la mort du processus. Une JVM qui se
# fige — deadlock, pool JDBC epuise — reste "vivante" et ne repond plus.
# Le healthcheck attrape ce cas la (avec le conteneur autoheal).
# start-period genereux : un Pi 3B+ met plus d une minute a demarrer Spring.
HEALTHCHECK --interval=60s --timeout=10s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1

# L entrypoint remet /app/data a l utilisateur tlprice (les volumes crees par
# les versions precedentes appartiennent a root), puis lance Java SANS les
# droits root.
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
