# Vikunja Live Deployment Record & Handoff Playbook

## 🌐 Server & Environment Summary
- **Hostinger VPS IP:** `187.124.174.135`
- **Application Directory:** `/var/www/vikunja`
- **Internal Port:** `127.0.0.1:3456`
- **Target Subdomain:** `https://tasks.codella.cloud`
- **Reverse Proxy:** Host Nginx (`/etc/nginx/sites-available/vikunja.conf`)
- **Database:** PostgreSQL 16 Alpine (`vikunja-db`)
- **Automated Backup:** `/var/www/vikunja/backup.sh` (Cron: `0 4 * * *`)
- **Co-existing Sites (Untouched):**
  - `thebestdoctor` (`thebestdoctor.cloud` / `api.thebestdoctor.cloud` on ports 3000, 8000)
  - `codella` (`codella.cloud` on port 3001)

---

## 📋 Checklist Verification & Next Steps

### 1. Hosting, Domain & Security (SSL)
- [x] Docker stack deployed at `/var/www/vikunja`.
- [x] Host Nginx reverse proxy configured and verified for `tasks.codella.cloud`.
- [ ] **Action Required:** Add DNS Record in Cloudflare/Hostinger DNS:
  - **Type:** `A`
  - **Name:** `tasks`
  - **Points to:** `187.124.174.135`
- [ ] Once DNS propagates, issue the Let's Encrypt SSL certificate by running:
  ```bash
  certbot --nginx -d tasks.codella.cloud
  ```

### 2. Instant Notification System (SMTP & Outbound Webhooks)
- [x] Webhook engine & background worker enabled (`VIKUNJA_WEBHOOKS_ENABLED: "true"`).
- [ ] To enable SMTP emails, edit `/var/www/vikunja/.env`:
  ```ini
  MAIL_ENABLED=true
  MAIL_HOST=smtp.yourprovider.com
  MAIL_PORT=587
  MAIL_USER=your_user
  MAIL_PASSWORD=your_password
  MAIL_FROM=noreply@codella.cloud
  ```
  Then restart Vikunja: `docker compose -f /var/www/vikunja/docker-compose.yml up -d vikunja`

### 3. Access Control & Registration Lock
- [x] `ENABLE_REGISTRATION=true` is currently set for the first signup.
- [ ] **Action:** Visit `https://tasks.codella.cloud` and register the primary admin account.
- [ ] **Action:** Lock registration permanently by running:
  ```bash
  sed -i 's/ENABLE_REGISTRATION=true/ENABLE_REGISTRATION=false/g' /var/www/vikunja/.env
  docker compose -f /var/www/vikunja/docker-compose.yml up -d vikunja
  ```

### 4. Workspace & Privacy Structure
- Create top-level Projects/Namespaces for departments.
- Ensure personal member sub-projects are not shared globally to enforce strict isolation.

### 5. Productivity Features Verification
- Verify **Stopwatch / Time Tracking** inside task modals.
- Verify **Gantt Chart view** tab inside projects.

### 6. Automated Data Backups
- [x] Backup script verified at `/var/www/vikunja/backup.sh`.
- [x] Daily cron job scheduled at 4:00 AM:
  ```cron
  0 4 * * * /var/www/vikunja/backup.sh >> /var/log/vikunja_backup.log 2>&1
  ```
