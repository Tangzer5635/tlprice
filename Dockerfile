# ---------- 1. Build du frontend ----------
# Node 22 : requis par Vite 8 (^20.19 || >=22.12).
FROM node:22-alpine AS front
WORKDIR /front

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./

RUN npm run build

# ---------- 2. Build du backend (avec le front dans static/) ----------
FROM maven:3.9-eclipse-temurin-21 AS back
WORKDIR /back
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=front /front/dist ./src/main/resources/static
RUN mvn -q package -DskipTests

# ---------- 3. Image finale ----------
# Pas d alpine : eclipse-temurin:21-jre-alpine n existe pas pour arm64.
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl n est pas dans l image de base : le HEALTHCHECK en a besoin.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=back /back/target/*.jar app.jar
RUN mkdir -p /app/data/sauvegardes
VOLUME /app/data
EXPOSE 8081
# Le Pi 3B+ n a qu 1 Go : on borne le tas pour eviter l OOM killer.
ENV JAVA_OPTS="-Xmx512m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC"

# restart: unless-stopped ne couvre que la mort du processus. Une JVM qui se
# fige — deadlock, pool JDBC epuise, OOM sans kill — reste "vivante" et ne
# repond plus. Le healthcheck attrape ce cas la.
# start-period genereux : un Pi 3B+ met plus d une minute a demarrer Spring.
HEALTHCHECK --interval=60s --timeout=10s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
