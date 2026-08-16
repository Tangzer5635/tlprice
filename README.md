<p align="center">
  <img src="docs/banniere.png" alt="tlprice" width="100%">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-c74634?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-6db33f?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5">
  <img src="https://img.shields.io/badge/React-19-61dafb?style=flat-square&logo=react&logoColor=black" alt="React 19">
  <img src="https://img.shields.io/badge/TypeScript-5.9-3178c6?style=flat-square&logo=typescript&logoColor=white" alt="TypeScript 5.9">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169e1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL 16">
  <img src="https://img.shields.io/badge/Docker-arm64-2496ed?style=flat-square&logo=docker&logoColor=white" alt="Docker arm64">
</p>

---

-- Le problème

Ton application bancaire affiche **1 240 €**.

Sauf que le loyer part dans quatre jours, l'assurance dans six, et la carte du restaurant de samedi n'est toujours pas passée. Le vrai chiffre — celui qui répond à « est-ce que je peux me le permettre ? » — n'est écrit nulle part. Tu le recalcules de tête, plusieurs fois par semaine, et tu te trompes.

**tlprice existe pour afficher ce chiffre-là.**

## La réponse

```
solde réel  +  crédits à venir  −  débits à venir  −  transactions pas encore débitées
```

Un chiffre, une date, et la liste de ce qui l'explique. Tout le reste de l'application sert à rendre ces quatre termes justes.

<br>

## Ce que ça fait

### 📊 La projection

Le solde à la date de ton choix — dans 7 jours, à la fin du mois, dans 12 mois. Une courbe qui montre le passé constaté en trait plein et le futur projeté en pointillé, avec ton seuil d'alerte en repère. Les mouvements sont groupés par jour, avec le net de chaque journée.

### 📅 Le calendrier

Une vue mensuelle où chaque case porte ses prélèvements et le solde en fin de journée. Un mode **cycle de paie** (du 25 au 24) parce que ton mois ne commence pas le 1er. Chaque occurrence peut être ajustée ou annulée sans toucher à l'opération récurrente elle-même — le loyer augmente une fois, pas pour toujours.

### 🔍 Le contrôle

L'écart silencieux est ce qui fait dériver une projection : un abonnement qui prend deux euros, un loyer revalorisé. tlprice confronte ce qu'il annonçait à ce que la banque a réellement débité, et te propose de corriger l'occurrence ou l'opération.

Et il **apprend** : confirmer un rapprochement incertain crée automatiquement la règle de renommage correspondante. Le mois suivant, `PRLV SEPA SCI DES TILLEULS 2024` est reconnu comme ton loyer sans que tu aies rien à faire.

### 🎲 Les scénarios

« Et si j'achète ça à 400 € le 12 ? »

Un scénario est une liste de mouvements hypothétiques. Tu le sélectionnes sur l'écran Solde, et la projection l'intègre — avec le solde **sans** le scénario affiché juste à côté et l'écart entre les deux. Les hypothèses ne sont jamais persistées comme des mouvements réels : elles disparaissent dès que tu désélectionnes.

### 💰 La capacité d'épargne

Combien tu peux virer sur ton livret sans passer sous ton seuil.

Le raisonnement est affiché à côté du chiffre : on parcourt la période, on retient le solde le plus bas atteint, on retranche le seuil. C'est ce creux qui fixe la limite, pas le solde d'aujourd'hui. Un montant seul serait un oracle ; avec le creux, tu peux le contester.

### 🔔 Les notifications

Un récapitulatif quotidien par push iOS ou par email, précédé d'une synchronisation bancaire automatique. Chaque appareil a **sa propre heure** : le téléphone à midi, l'ordinateur à 22 h.

### 🏦 La synchronisation bancaire

Via Enable Banking (DSP2). Les identifiants bancaires ne transitent jamais par tlprice ; chaque clé privée est chiffrée en AES-256-GCM et ne donne accès qu'au compte de son propriétaire.

Sans banque compatible, l'application reste entièrement utilisable : le solde se corrige à la main et la projection fonctionne à l'identique.

<br>

## Pensé pour l'iPhone

Installée depuis l'écran d'accueil, tlprice se comporte comme une application native.

Barre du bas à cinq onglets avec le solde au centre, sur le point le plus atteignable au pouce. Tirer vers le bas pour synchroniser. Feuilles basses plutôt que fenêtres centrées, parce qu'une modale au milieu de l'écran demande au pouce de remonter. Calendrier à points colorés — à 50 px de large, un libellé tronqué à trois lettres n'apprend rien — avec balayage horizontal pour changer de mois.

<br>

## Captures

> Dépose tes captures dans `docs/captures/` sous ces noms et elles apparaîtront ici.

| Solde | Calendrier | Contrôle |
|:--:|:--:|:--:|
| <img src="docs/captures/solde.png" width="230"> | <img src="docs/captures/calendrier.png" width="230"> | <img src="docs/captures/controle.png" width="230"> |

<br>

## Sous le capot

```mermaid
flowchart LR
    subgraph nav["Navigateur / PWA"]
        R["React 19<br/>Vite · TypeScript"]
    end
    subgraph pi["Raspberry Pi · Docker"]
        S["Spring Boot 3.5<br/>Java 21"]
        P[("PostgreSQL 16")]
        A["autoheal"]
    end
    EB["Enable Banking<br/>DSP2"]

    R -->|"REST · session + CSRF"| S
    S --> P
    S -->|"JWT · RS256"| EB
    A -.->|"surveille /actuator/health"| S
```

Architecture strictement en couches : `entities` → `repositories` → `services` → `controllers`. Le frontend est compilé puis servi par le backend — un seul conteneur, un seul port.

**Backend** — Spring Boot 3.5, Java 21, Spring Security (session + CSRF + cookie de connexion persistant), JPA/Hibernate, Apache POI pour l'export Excel, BouncyCastle pour le Web Push VAPID, AES-256-GCM pour les secrets en base.

**Frontend** — React 19, Vite 8, TypeScript 5.9, react-router 7. Aucune bibliothèque de composants : le CSS est écrit à la main, autour d'Archivo et IBM Plex Mono.

**Infrastructure** — Image Docker multi-étapes construite pour `linux/arm64`, tournant sur un Raspberry Pi 3B+ derrière un tunnel Cloudflare. `HEALTHCHECK` sur la sonde de vivacité, avec un conteneur `autoheal` — Docker marque « unhealthy » mais ne redémarre rien de lui-même.

<br>

## Installation

### Production

```bash
git clone https://github.com/<toi>/tlprice.git && cd tlprice
cp .env.example .env      # puis remplis-le
docker compose up -d --build
```

L'application écoute sur le port 8081. Mets un reverse proxy HTTPS devant — HTTPS est obligatoire, Enable Banking refuse les URL de renvoi en clair et le Web Push n'existe pas sans. Avec Caddy, deux lignes suffisent :

```
tondomaine.fr {
    reverse_proxy localhost:8081
}
```

Génère les secrets ainsi :

```bash
openssl rand -base64 32          # TLPRICE_CLE_CHIFFREMENT — à sauvegarder
npx web-push generate-vapid-keys # TLPRICE_VAPID_* — une seule fois
```

> ⚠️ La perte de `TLPRICE_CLE_CHIFFREMENT` rend les connexions bancaires et la configuration SMTP définitivement indéchiffrables.

### Raspberry Pi

```bash
docker buildx build --platform linux/arm64 -t <compte>/tlprice:latest --push .
```

Puis, sur le Pi, `docker compose pull && docker compose up -d`. Le script `scripts/verifier-prod.sh` contrôle la version en ligne, l'état de santé, les variables d'environnement et les sauvegardes.

### Développement

```bash
cd backend  && mvn spring-boot:run   # H2 fichier, rien à configurer
cd frontend && npm install && npm run dev
```

Les variables locales vont dans `backend/.env`, lu automatiquement au démarrage. Tests : `mvn test`.

<br>

## Sécurité et vie privée

Aucune donnée ne quitte ton serveur. Pas de télémétrie, pas d'analytics, pas de tiers — hormis ta banque, à qui tu parles directement via ton propre enregistrement Enable Banking.

Les clés privées bancaires et la configuration SMTP sont chiffrées en AES-256-GCM. Le cookie de connexion est signé avec une clé **dérivée** de la clé maîtresse par SHA-256, jamais avec la clé elle-même : deux usages distincts méritent deux secrets distincts. Les mots de passe sont hachés en BCrypt. Toutes les requêtes sont isolées par utilisateur au niveau du service.

Une sauvegarde JSON complète est écrite chaque nuit, en écriture atomique avec rotation sur 30 fichiers, et téléchargeable depuis l'application.

<br>

## Feuille de route

- [ ] **Flyway** — migrations versionnées, avec `pg_dump -s` en référence de départ
- [ ] **Multi-comptes** — courant, livret et joint, avec projection consolidée
- [ ] **Import de fichier** — OFX et QIF, pour fonctionner avec n'importe quelle banque sans agrégateur
- [ ] **Cloudflare Access** devant l'application, contre le bruteforce
- [ ] Tests sur `SoldeService`

<br>

## Licence

Projet personnel, publié pour consultation. Aucune licence libre n'est accordée : tous droits réservés. Ouvre une issue si tu veux en discuter.

<br>

<p align="center">
  <sub>Construit sur un Raspberry Pi 3B+, parce qu'il était déjà allumé.</sub>
</p>
#   t l p r i c e 
 
 #   t l p r i c e 
 
 
