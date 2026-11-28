#!/usr/bin/env bash
# Generates the Ed25519 keypair for the signed remote-config channel.
#
#  remote-config-public.pem   → the public half. Its SPKI (base64 DER) value
#                               is pinned in RemoteConfigChannel.PUBLIC_KEY_B64
#                               in the app; changing it requires an app release.
#  remote-config-private.pem  → the private half. NEVER commit it. Store the
#                               base64 of the PEM as the GitHub repository
#                               secret REMOTE_CONFIG_SIGNING_KEY — the release
#                               workflow signs with it.
set -euo pipefail

openssl genpkey -algorithm ed25519 -out remote-config-private.pem
openssl pkey -in remote-config-private.pem -pubout -out remote-config-public.pem

echo "public key (SPKI base64 — pin in RemoteConfigChannel.PUBLIC_KEY_B64):"
openssl pkey -in remote-config-public.pem -pubin -outform DER | base64 -w0
echo
echo "private key (base64 of PEM — store as GitHub secret REMOTE_CONFIG_SIGNING_KEY):"
base64 -w0 remote-config-private.pem
echo
