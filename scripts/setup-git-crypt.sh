#!/usr/bin/env bash
# setup-git-crypt.sh
#
# One-time setup script for git-crypt in this repository.
# Run this once after cloning to initialize encryption and add your GPG key.
#
# Usage:
#   ./scripts/setup-git-crypt.sh              # Initialize + prompt for GPG key
#   ./scripts/setup-git-crypt.sh <GPG_KEY_ID> # Initialize + add specific GPG key
#   ./scripts/setup-git-crypt.sh --unlock     # Unlock repo (for collaborators already added)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# Check prerequisites
check_prerequisites() {
    if ! command -v git-crypt &>/dev/null; then
        error "git-crypt is not installed. Install it first:
  macOS:  brew install git-crypt
  Ubuntu: sudo apt-get install git-crypt
  Arch:   sudo pacman -S git-crypt"
    fi

    if ! command -v gpg &>/dev/null; then
        error "gpg is not installed. Install it first:
  macOS:  brew install gnupg
  Ubuntu: sudo apt-get install gnupg
  Arch:   sudo pacman -S gnupg"
    fi

    info "Prerequisites satisfied (git-crypt, gpg)"
}

# Initialize git-crypt in the repo
init_git_crypt() {
    if [ -d ".git-crypt" ]; then
        info "git-crypt is already initialized in this repository."
        return 0
    fi

    info "Initializing git-crypt..."
    git-crypt init
    info "git-crypt initialized successfully."
}

# Add a GPG user
add_gpg_user() {
    local key_id="$1"

    if [ -z "$key_id" ]; then
        echo ""
        info "Available GPG keys:"
        gpg --list-keys --keyid-format long 2>/dev/null || true
        echo ""
        read -rp "Enter the GPG key ID to authorize (email or key ID): " key_id
    fi

    if [ -z "$key_id" ]; then
        warn "No GPG key provided. You can add one later with:"
        warn "  git-crypt add-gpg-user <GPG_KEY_ID>"
        return 0
    fi

    info "Adding GPG user: $key_id"
    git-crypt add-gpg-user "$key_id"
    info "User $key_id has been authorized to decrypt this repository."
}

# Unlock the repo (for collaborators)
unlock_repo() {
    info "Unlocking repository with your GPG key..."
    git-crypt unlock
    info "Repository unlocked. Encrypted files are now readable."
}

# Export symmetric key (alternative to GPG)
export_key() {
    local key_file="${1:-git-crypt-key}"
    info "Exporting symmetric key to: $key_file"
    git-crypt export-key "$key_file"
    info "Key exported. Share this file securely with collaborators."
    warn "IMPORTANT: Never commit this key file to the repository!"
    warn "Add '$key_file' to .gitignore if it's in the repo directory."
}

# Verify encryption status
verify() {
    info "Encryption status for tracked files:"
    echo ""
    git-crypt status 2>/dev/null || warn "No files matched encryption patterns yet."
    echo ""
    info "Protected directories: blueprints/, chest/"
    info "Protected file extensions: *.secret, *.key, *.pem"
}

# Main
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [OPTIONS] [GPG_KEY_ID]"
        echo ""
        echo "Options:"
        echo "  (no args)       Initialize git-crypt and prompt for GPG key"
        echo "  <GPG_KEY_ID>    Initialize and add the specified GPG key"
        echo "  --unlock        Unlock the repo (for authorized collaborators)"
        echo "  --export-key    Export symmetric key for sharing"
        echo "  --verify        Show encryption status of files"
        echo "  --help          Show this help message"
        ;;
    --unlock)
        check_prerequisites
        unlock_repo
        ;;
    --export-key)
        check_prerequisites
        export_key "${2:-git-crypt-key}"
        ;;
    --verify)
        check_prerequisites
        verify
        ;;
    *)
        check_prerequisites
        init_git_crypt
        add_gpg_user "${1:-}"
        verify
        ;;
esac
