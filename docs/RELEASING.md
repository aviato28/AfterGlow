# Releasing Afterglow (owner steps)

1. GitHub → repo → Settings → Secrets and variables → Actions → New repository secret. Add two:
   `AFTERGLOW_BOT_TOKEN` (a fine-grained personal access token for this repo only, permission "Secrets: write")
   and `KEY_BACKUP_PASSPHRASE` (any long phrase you will keep).
2. Actions → "One-time: create signing key" → Run workflow. It refuses to run if a key already exists.
3. When it finishes, open the run and download the `afterglow-key-backup` artifact within a day (it is encrypted).
   Keep the `.gpg` file and your passphrase somewhere safe. Never replace the key: installed copies could no longer update.
4. Actions → "Release" → Run workflow → enter a version like `0.1.0`. The signed APK appears under Releases.
