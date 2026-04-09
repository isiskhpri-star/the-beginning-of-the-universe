# Security: Galaxy Defence Encryption Framework

This repository uses [git-crypt](https://github.com/AGWA/git-crypt) to transparently encrypt sensitive files at rest. Files in protected directories are encrypted when pushed to GitHub and decrypted locally for authorized users.

## Protected Assets

| Directory / Pattern | Purpose |
|---|---|
| `blueprints/` | Galaxy conquest blueprints and strategic plans |
| `chest/` | Treasure chest — keys, credentials, and valuable assets |
| `*.secret` | Secret files (any location) |
| `*.key` | Key files (any location) |
| `*.pem` | Certificate/key files (any location) |

## How It Works

- **At rest on GitHub**: Protected files appear as encrypted binary blobs. Even if someone gains read access to the repo, they cannot read the contents.
- **Locally for authorized users**: Files are automatically decrypted when you `git pull` or `git checkout`, and encrypted when you `git push`. It's completely transparent — you work with plain files as usual.

## Quick Start

### First-Time Setup (Repo Owner)

1. **Install prerequisites**:
   ```bash
   # macOS
   brew install git-crypt gnupg

   # Ubuntu/Debian
   sudo apt-get install git-crypt gnupg
   ```

2. **Run the setup script**:
   ```bash
   chmod +x scripts/setup-git-crypt.sh
   ./scripts/setup-git-crypt.sh
   ```
   This initializes git-crypt and prompts you to add your GPG key.

3. **Add your GPG key** (if you don't have one yet):
   ```bash
   gpg --full-generate-key   # Generate a new GPG key pair
   ./scripts/setup-git-crypt.sh your@email.com
   ```

### Adding Collaborators

**Option A: GPG-based (recommended)**
```bash
# Import the collaborator's public GPG key
gpg --import collaborator-public-key.asc

# Authorize them
git-crypt add-gpg-user collaborator@email.com

# Commit and push (this creates an authorization commit)
git push
```

The collaborator can then unlock the repo:
```bash
git clone <repo-url>
cd the-beginning-of-the-universe
./scripts/setup-git-crypt.sh --unlock
```

**Option B: Symmetric key (simpler but less secure)**
```bash
# Export the symmetric key
./scripts/setup-git-crypt.sh --export-key

# Share 'git-crypt-key' securely (NOT via email or chat!)
# The collaborator unlocks with:
git-crypt unlock /path/to/git-crypt-key
```

### Verify Encryption Status

```bash
./scripts/setup-git-crypt.sh --verify
```

## Adding New Protected Paths

Edit `.gitattributes` to add new encryption rules:

```gitattributes
# Encrypt a new directory
my-secrets/**  filter=git-crypt diff=git-crypt

# Encrypt a specific file
config/production.yml  filter=git-crypt diff=git-crypt
```

After editing `.gitattributes`, commit and push. New files matching the patterns will be encrypted automatically.

## Important Notes

- **Never commit the symmetric key** (`git-crypt-key`) to the repository.
- **`.gitattributes` must remain unencrypted** — it defines the encryption rules.
- **Files already committed unencrypted** will NOT be retroactively encrypted. If you accidentally commit a sensitive file without encryption, you must rewrite history to remove it.
- **git-crypt works at the file level** — it does not encrypt filenames, only contents.
- If you suspect a key has been compromised, rotate the git-crypt key and re-encrypt.

## Troubleshooting

| Problem | Solution |
|---|---|
| `git-crypt: not found` | Install git-crypt (see Quick Start) |
| Files appear as binary garbage | Run `git-crypt unlock` or the setup script with `--unlock` |
| "No matching GPG key" on unlock | Your GPG key hasn't been authorized — ask the repo owner to run `git-crypt add-gpg-user` |
| Changes to `.gitattributes` not taking effect | Commit the `.gitattributes` changes, then run `git-crypt status` to verify |
