---
description: Deploy lundedev-core to production.
---

1. Check for local changes.
    ```bash
    git status
    ```

2. Add changes.
    ```bash
    git add .
    ```

3. Commit changes.
    ```bash
    git commit -m "Deployment from Antigravity"
    ```

4. Push to origin.
    ```bash
    git push origin main
    ```

5. Wait for build.
    ```bash
    gh run watch
    ```

6. Deploy on server.
    ```bash
    ssh lundedev "cd /home/vegard/homelab && docker compose pull && docker compose up -d"
    ```