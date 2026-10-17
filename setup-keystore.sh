#!/usr/bin/env bash
set -euo pipefail

CERT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/certs"
mkdir -p "$CERT_DIR"

KEYSTORE_FILE="$CERT_DIR/keystore.p12"
PUBLIC_CERT_FILE="$CERT_DIR/claim-signer-public.crt"
PASSWORD="${KEYSTORE_PASSWORD:-changeit_local_poc}"
ALIAS="${KEYSTORE_ALIAS:-claimsigner}"

rm -f "$KEYSTORE_FILE" "$PUBLIC_CERT_FILE"

echo "Generating self-signed PKCS#12 keystore (RSA 2048, SHA256withRSA)..."
keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -validity 365 \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=Claim Report PoC Signer, OU=Digital Claims Architecture, O=OpenSource Insurance PoC, L=Bangkok, C=TH"

echo "Exporting public certificate..."
keytool -exportcert \
  -alias "$ALIAS" \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -rfc \
  -file "$PUBLIC_CERT_FILE"

echo "Done! PKCS#12 keystore generated at: $KEYSTORE_FILE"
