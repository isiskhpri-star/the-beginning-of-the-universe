#!/usr/bin/env bash
#
# Meta-protection guard — protects the code that protects the code.
#
#   ./scripts/protection-guard.sh seal     Regenerate protection/manifest.sha256
#   ./scripts/protection-guard.sh verify   Fail if any protected file changed
#   ./scripts/protection-guard.sh status   Human-readable report
#
# Verification rules
#   * plaintext file  -> SHA-256 of contents must match the manifest
#   * encrypted file  -> must be listed in .gitattributes with the git-crypt
#                        filter, and must be either decrypted-and-matching
#                        (key present) or a real git-crypt blob (no key)
#   * any protected file that disappears is a failure
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATHS_FILE="$REPO_ROOT/protection/protected-paths.txt"
MANIFEST="$REPO_ROOT/protection/manifest.sha256"
GITCRYPT_MAGIC=$'\x00GITCRYPT'

die() { echo "protection-guard: $*" >&2; exit 1; }

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

protected_paths() {
  [ -f "$PATHS_FILE" ] || die "missing $PATHS_FILE"
  sed -e 's/#.*//' -e 's/[[:space:]]*$//' "$PATHS_FILE" | grep -v '^$'
}

# Is the path covered by a git-crypt filter rule in .gitattributes?
is_encrypted_path() {
  git -C "$REPO_ROOT" check-attr filter -- "$1" 2>/dev/null | grep -q 'filter: git-crypt'
}

is_gitcrypt_blob() {
  [ "$(head -c 9 "$1" 2>/dev/null || true)" = "$GITCRYPT_MAGIC" ]
}

seal() {
  local out; out="$(mktemp)"
  {
    echo "# Meta-protection manifest — regenerate with: ./scripts/protection-guard.sh seal"
    echo "# format: <sha256|ENCRYPTED>  <path>"
  } >"$out"
  local p
  while IFS= read -r p; do
    [ -f "$REPO_ROOT/$p" ] || die "protected file missing: $p"
    if is_encrypted_path "$p" && is_gitcrypt_blob "$REPO_ROOT/$p"; then
      printf 'ENCRYPTED  %s\n' "$p" >>"$out"
    else
      printf '%s  %s\n' "$(sha256 "$REPO_ROOT/$p")" "$p" >>"$out"
    fi
  done < <(protected_paths)
  mv "$out" "$MANIFEST"
  echo "sealed $(grep -vc '^#' "$MANIFEST") protected files -> protection/manifest.sha256"
}

verify() {
  [ -f "$MANIFEST" ] || die "missing manifest; run: ./scripts/protection-guard.sh seal"
  local failures=0 checked=0 expected path actual

  # Every protected path must be in the manifest.
  while IFS= read -r path; do
    grep -q "  $path\$" "$MANIFEST" || { echo "UNSEALED  $path (not in manifest)"; failures=$((failures + 1)); }
  done < <(protected_paths)

  while read -r expected path; do
    case "$expected" in '#'*|'') continue ;; esac
    checked=$((checked + 1))

    if [ ! -f "$REPO_ROOT/$path" ]; then
      echo "MISSING   $path"; failures=$((failures + 1)); continue
    fi
    if ! grep -qx -- "$path" <(protected_paths); then
      echo "ORPHAN    $path (in manifest but no longer protected)"; failures=$((failures + 1)); continue
    fi

    if [ "$expected" = "ENCRYPTED" ] || is_encrypted_path "$path"; then
      if ! is_encrypted_path "$path"; then
        echo "UNGUARDED $path (git-crypt filter removed from .gitattributes)"
        failures=$((failures + 1)); continue
      fi
      if is_gitcrypt_blob "$REPO_ROOT/$path"; then
        echo "OK        $path (encrypted at rest)"; continue
      fi
      if [ "$expected" != "ENCRYPTED" ]; then
        actual="$(sha256 "$REPO_ROOT/$path")"
        [ "$actual" = "$expected" ] && { echo "OK        $path (decrypted, hash matches)"; continue; }
        echo "TAMPERED  $path"; echo "          expected $expected"; echo "          actual   $actual"
        failures=$((failures + 1)); continue
      fi
      echo "OK        $path (decrypted locally, not hashed)"
      continue
    fi

    actual="$(sha256 "$REPO_ROOT/$path")"
    if [ "$actual" = "$expected" ]; then
      echo "OK        $path"
    else
      echo "TAMPERED  $path"
      echo "          expected $expected"
      echo "          actual   $actual"
      failures=$((failures + 1))
    fi
  done <"$MANIFEST"

  echo
  if [ "$failures" -ne 0 ]; then
    echo "protection-guard: FAILED — $failures of $checked protected files did not verify."
    echo "If the change is intentional, run './scripts/protection-guard.sh seal' and have a"
    echo "security owner review the manifest diff."
    exit 1
  fi
  echo "protection-guard: OK — $checked protected files verified."
}

case "${1:-verify}" in
  seal)   seal ;;
  verify) verify ;;
  status) verify || true ;;
  *)      die "unknown command '${1}'; use seal|verify|status" ;;
esac
