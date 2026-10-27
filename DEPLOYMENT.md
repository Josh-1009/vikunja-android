# Vikunja Deployment on Hostinger VPS

A production-ready Docker Compose setup for [Vikunja](https://vikunja.io/) with PostgreSQL and Caddy (automatic HTTPS/SSL).

---

## Prerequisites

1. **Hostinger VPS** running Ubuntu 22.04 / 24.04 or Debian 12.
2. **Domain or Subdomain** (e.g. `tasks.example.com` or `vikunja.example.com`) pointed via **DNS A Record** to your Hostinger VPS IP.
3. **SSH Access** to your VPS.

---

## Step 1: Prepare your VPS (Install Docker & Docker Compose)

SSH into your Hostinger VPS:
```bash
ssh root@<YOUR_VPS_IP>
```

Update packages and install Docker:
```bash
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo systemctl enable --now docker
```

---

## Step 2: Set up Vikunja Directory

On your VPS, create a directory for Vikunja and copy these files:
```bash
mkdir -p /opt/vikunja
cd /opt/vikunja
```

Create your `.env` file from the template:
```bash
cp .env.example .env
```

Generate secure secrets for JWT and DB:
```bash
# Generate JWT Secret
openssl rand -base64 32

# Generate DB Password
openssl rand -hex 16
```

Update your `.env` with:
- `VIKUNJA_PUBLIC_URL` = `https://tasks.yourdomain.com/` (Make sure to include the trailing slash `/`)
- `VIKUNJA_JWT_SECRET` = (generated string)
- `DB_PASSWORD` = (generated string)

Update [Caddyfile](file:///home/josh/Drive_D/SAAS/Vikunja/Caddyfile) with your domain name (replace `tasks.yourdomain.com`).

---

## Step 3: Start Vikunja

Run:
```bash
docker compose up -d
```

Check the logs to verify everything started smoothly:
```bash
docker compose logs -f
```

---

## Step 4: Initial Setup & Security

1. Open your browser and navigate to `https://tasks.yourdomain.com`.
2. Register your admin account.
3. **(Recommended)** Disable open registration to prevent unauthorized users from registering:
   Edit `.env`:
   ```ini
   ENABLE_REGISTRATION=false
   ```
   Restart the Vikunja container:
   ```bash
   docker compose up -d vikunja
   ```

---

## Step 5: Backup & Maintenance

### Backup Database
```bash
docker exec -t vikunja-db pg_dump -U vikunja vikunja > /opt/vikunja/vikunja_backup_$(date +%F).sql
```

### Update Vikunja to Latest
```bash
docker compose pull
docker compose up -d
```
