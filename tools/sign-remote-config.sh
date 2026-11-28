#!/usr/bin/env bash
# Signs remote-config/remote-config.json with the Ed25519 private key.
# The key is NOT stored in this repository — set it as the environment
# variable REMOTE_CONFIG_SIGNING_KEY (base64 of the PEM, as stored in the
# GitHub secret) or point REMOTE_CONFIG_KEY_FILE at the PEM file.
#
# Output: remote-config/remote-config.json.sig (base64, no line wrapping) —
# exactly what the app fetches from "<channel-url>.sig".
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "${REMOTE_CONFIG_KEY_FILE:-}" ]]; then
  KEY="$REMOTE_CONFIG_KEY_FILE"
elif [[ -n "${REMOTE_CONFIG_SIGNING_KEY:-}" ]]; then
  KEY="$(mktemp)"
  trap 'rm -f "$KEY"' EXIT
  echo "$REMOTE_CONFIG_SIGNING_KEY" | base64 -d > "$KEY"
else
  echo "error: set REMOTE_CONFIG_SIGNING_KEY (base64 PEM) or REMOTE_CONFIG_KEY_FILE" >&2
  exit 1
fi

openssl pkeyutl -sign -inkey "$KEY" -rawin \
  -in remote-config/remote-config.json \
  | base64 -w0 > remote-config/remote-config.json.sig

echo "signed: remote-config/remote-config.json.sig"
cat remote-config/remote-config.json.sig; echo
