# Installer et exploiter tlprice sur un Raspberry Pi (SSH)

Ce guide couvre trois cas :

- **A.** Migrer l'installation actuelle (celle qui tourne déjà sur `tlprices.tangzersite.com`) vers la v4.3.
- **B.** Installer sur un Pi neuf.
- **C.** L'exploitation courante : mises à jour, sauvegardes, restauration, retour arrière.

---

## 0. Prérequis matériels

| | Minimum | Recommandé |
|---|---|---|
| Pi | **3B+** (1 Go) | 4 ou 5 (2 Go et plus) |
| Système | Raspberry Pi OS **Lite 64-bit** | idem |
| Stockage | carte SD A1/A2 de 16 Go | **SSD en USB** pour le système, **clé USB** pour les sauvegardes |

> ⚠️ « Pi B+ » sans chiffre désigne le Pi 1 B+ (ARMv6, 512 Mo) : il **ne peut pas** faire tourner Java 21 + PostgreSQL. Le README parle d'un **3B+** : c'est bien le minimum. Vérifie avec `cat /proc/device-tree/model` et `uname -m` (doit afficher `aarch64`).

Une carte SD finit toujours par mourir sur un Pi allumé 24 h/24, c'est pourquoi les sauvegardes partent maintenant **hors de la carte**.

---

## A. Migrer l'installation actuelle

Tout se fait en SSH : `ssh pi@<ip-du-pi>` (ou ton utilisateur).

### A1. Fusionner et laisser GitHub construire l'image

1. Fusionne la branche dans `main` sur GitHub.
2. Onglet **Actions** : le workflow **CI** lance les tests, puis publie `ghcr.io/tangzer5635/tlprice:latest` (image arm64). Compte 6 à 10 minutes la première fois.
3. Sur GitHub, ouvre **ton profil → Packages → tlprice → Package settings** et passe la visibilité en **Public**. Sinon le Pi doit s'authentifier :

```bash
docker login ghcr.io -u Tangzer5635
```

Pour cette commande, utilise comme mot de passe un *Personal Access Token (classic)* avec le seul droit `read:packages`.

### A2. Sauvegarder l'existant (avant de toucher à quoi que ce soit)

```bash
cd ~/tlprices
```

```bash
docker compose exec -T db pg_dump -U tlprice -d tlprice | gzip > ~/avant-v43.sql.gz
```

```bash
cp .env ~/env.avant-v43 && cp docker-compose.yml ~/compose.avant-v43.yml
```

Note le nom de tes volumes, il sert plus bas :

```bash
docker volume ls
```

Tu dois voir `tlprices_donnees_db` et `tlprices_donnees_app`. Si le préfixe est différent, retiens-le.

### A3. Remplacer le dossier par un clone du dépôt

```bash
cd ~ && docker compose -f tlprices/docker-compose.yml down
```

```bash
mv ~/tlprices ~/tlprices.ancien && git clone https://github.com/Tangzer5635/tlprice.git ~/tlprices
```

```bash
cp ~/tlprices.ancien/.env ~/tlprices/.env && chmod 600 ~/tlprices/.env
```

`docker compose down` ne supprime **pas** les volumes : la base reste intacte.

### A4. Compléter le `.env`

Le nouveau `docker-compose.yml` lit **toute** sa configuration dans `.env`. Or certaines valeurs étaient écrites en dur dans l'ancien `docker-compose.yml`, notamment les clés VAPID. Récupère-les :

```bash
grep -nE 'TLPRICE_|MAIL_|DB_' ~/tlprices.ancien/docker-compose.yml
```

Pour chaque valeur écrite en clair (et pas sous la forme `${...}`), ajoute une ligne `CLE=valeur` dans `.env` :

```bash
nano ~/tlprices/.env
```

**Ne régénère pas** les clés VAPID : recopie-les, sinon tous tes appareils devront se réabonner aux notifications.

Ajoute aussi ces lignes (adapte le préfixe à ce que `docker volume ls` a affiché) :

```text
TLPRICE_VOLUME_PREFIXE=tlprices
TLPRICE_INSCRIPTION_OUVERTE=false
TLPRICE_CONNEXION_DUREE_HEURES=720
TLPRICE_SAUVEGARDES=/home/pi/tlprice-sauvegardes
```

Puis vérifie le format :

```bash
cd ~/tlprices && bash scripts/preparer-env-file.sh
```

### A5. Vérifier où tourne cloudflared

Le port de l'app n'écoute plus que sur `127.0.0.1` (il était ouvert en HTTP clair à tout le réseau local).

```bash
systemctl is-active cloudflared; docker ps --format '{{.Names}}' | grep -i cloudflared
```

- `active` (service système) ou conteneur en `network_mode: host` : rien à faire, le tunnel pointe sur `http://localhost:8082`.
- cloudflared dans un conteneur sur un réseau Docker, ou sur une autre machine : ajoute `TLPRICE_ECOUTE=0.0.0.0` dans `.env`.

### A6. Déployer

```bash
cd ~/tlprices && ./scripts/deployer.sh
```

Le script refuse de démarrer s'il ne trouve pas le volume de base attendu, plutôt que de créer une base vide. Il sauvegarde, télécharge l'image, redémarre et attend l'état `healthy` (jusqu'à 3 minutes sur un Pi 3B+).

Planifie ensuite la sauvegarde nocturne :

```bash
./scripts/installer-pi.sh
```

Le script ne touche pas à un `.env` existant. Réponds `o` à la question du cron (et du swap si on te la pose).

Enfin :

```bash
bash scripts/verifier-prod.sh
```

### A7. Sur l'iPhone

- Ouvre l'app : un bandeau « Nouvelle version disponible » apparaît, touche-le. Sinon, ferme l'app (balayage vers le haut) et rouvre-la.
- Une reconnexion peut être demandée une fois (le cookie de session est maintenant réservé au HTTPS).
- Les notifications continuent de fonctionner si tu as bien recopié les clés VAPID.

Si quelque chose ne va pas, reviens en arrière :

```bash
cd ~ && docker compose -f tlprices/docker-compose.yml down && mv tlprices tlprices.v43 && mv tlprices.ancien tlprices && cd tlprices && docker compose up -d
```

---

## B. Installation sur un Pi neuf

1. Avec **Raspberry Pi Imager**, flashe *Raspberry Pi OS Lite (64-bit)*. Dans les options : nom d'hôte, utilisateur, **SSH activé**, Wi-Fi si besoin.
2. Connecte-toi et mets le système à jour :

```bash
sudo apt update && sudo apt full-upgrade -y && sudo apt install -y git
```

3. Clone et lance l'installeur :

```bash
git clone https://github.com/Tangzer5635/tlprice.git ~/tlprices && cd ~/tlprices && ./scripts/installer-pi.sh
```

L'installeur vérifie le matériel, installe Docker (il faut ensuite se reconnecter en SSH et le relancer), règle le swap à 1 Go et génère le `.env` (clé AES, mot de passe PostgreSQL, clés VAPID). Il planifie la sauvegarde nocturne et démarre l'app.

4. Expose l'app en HTTPS avec **Cloudflare Tunnel** : Zero Trust → Networks → Tunnels → *Create a tunnel* → installe le connecteur sur le Pi avec la commande fournie. Ajoute ensuite un *Public hostname* `tlprices.tondomaine.com` qui pointe vers `http://localhost:8082`.
5. Sur l'iPhone, ouvre l'URL dans **Safari**, puis Partager → **Sur l'écran d'accueil**. Ouvre l'app depuis l'icône et crée ton compte : c'est le seul possible, les inscriptions se ferment ensuite.
6. Dans Réglages → Notifications, active le push sur l'appareil.

---

## C. Exploitation courante

### Mettre à jour

Pousse sur `main`, attends la fin du workflow **CI** sur GitHub, puis :

```bash
cd ~/tlprices && git pull && ./scripts/deployer.sh
```

`git pull` récupère les scripts et le compose, `deployer.sh` récupère l'image.

### Revenir à une version précédente

Chaque image est aussi publiée avec le tag `sha-<commit>`. Dans `.env` :

```text
TLPRICE_IMAGE=ghcr.io/tangzer5635/tlprice:sha-1a2b3c4
```

```bash
./scripts/deployer.sh
```

Supprime la ligne pour revenir à `latest`.

### Sauvegardes

| Quoi | Où | Quand |
|---|---|---|
| Dump PostgreSQL complet (`.sql.gz`) | `TLPRICE_SAUVEGARDES` (défaut `~/tlprice-sauvegardes`) | chaque nuit à 3 h 45 (cron), et avant chaque déploiement |
| Exports JSON de l'app | `…/json/` | copiés à chaque dump |
| `.env` | `…/env/` | à chaque changement |

Lancer une sauvegarde à la main :

```bash
./scripts/sauvegarde-pg.sh
```

**Mets `TLPRICE_SAUVEGARDES` sur une clé USB**, pas sur la carte SD : une sauvegarde sur le support qui tombe en panne ne sert à rien. Pour monter une clé USB de façon permanente, récupère d'abord son `UUID` :

```bash
sudo blkid
```

Puis ajoute la ligne de montage dans `/etc/fstab` :

```text
UUID=xxxx-xxxx  /mnt/sauvegardes  ext4  defaults,nofail  0  2
```

Pour une copie hors site, installe `rclone`, configure une destination (`rclone config`) puis renseigne `TLPRICE_RCLONE_DEST=gdrive:tlprice`. Le `.env` n'est jamais envoyé hors site, puisqu'il contient la clé de chiffrement.

Une fois par mois, copie aussi le `.env` sur ton ordinateur :

```bash
scp pi@<ip-du-pi>:tlprices/.env ./tlprice.env
```

### Restaurer

```bash
./scripts/restaurer-pg.sh ~/tlprice-sauvegardes/tlprice-AAAAMMJJ-HHMMSS.sql.gz
```

Le script sauvegarde d'abord l'état actuel, puis restaure. Sur un **nouveau** Pi, recopie d'abord le même `.env` : sans la même `TLPRICE_CLE_CHIFFREMENT`, les connexions bancaires et le SMTP sont illisibles.

### Diagnostiquer

```bash
bash scripts/verifier-prod.sh
```

```bash
docker compose logs --tail 100 app
```

```bash
docker stats --no-stream
```

### Inviter quelqu'un

Mets `TLPRICE_INSCRIPTION_OUVERTE=true` dans `.env`, puis lance `docker compose up -d`. La personne crée son compte. Remets ensuite `false` et relance `docker compose up -d`.

### Changer de mot de passe / appareil perdu

Dans l'app : Réglages → **Mot de passe**. Le changement déconnecte tous les autres appareils.

---

## Ce qui a changé côté sécurité (v4.3)

- **Faille corrigée** : `GET /api/solde` renvoyait l'utilisateur complet, **hash du mot de passe compris**. Si tu as partagé des captures réseau ou des exports de cette réponse, change ton mot de passe.
- Les **inscriptions sont fermées** par défaut. Avant, n'importe qui sur Internet pouvait se créer un compte sur ton Pi.
- **Anti-bruteforce** : 5 échecs sur un email ou 20 échecs depuis une IP bloquent les tentatives pendant 15 minutes (réponse 429).
- **En-têtes de sécurité** : Content-Security-Policy stricte, `frame-ancestors 'none'`, `Referrer-Policy` et `Permissions-Policy`.
- Le port n'écoute que sur `127.0.0.1`, et le cookie de session n'est envoyé qu'en HTTPS.
- Java ne tourne plus en root dans le conteneur.
- Il n'y a plus d'appel à Google Fonts : les polices sont servies par le Pi.
