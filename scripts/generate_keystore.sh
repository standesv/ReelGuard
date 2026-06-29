#!/bin/bash
# Exécuter ce script UNE SEULE FOIS sur votre ordinateur pour créer votre keystore de signature.
# Les valeurs générées seront à copier dans les Secrets GitHub.

set -e

KEYSTORE_FILE="reelguard.keystore"
KEY_ALIAS="reelguard-key"
KEY_STORE_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=')
KEY_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=')

echo "Generation du keystore..."

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$KEY_STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=ReelGuard, OU=Personal, O=Personal, L=FR, S=FR, C=FR"

SIGNING_KEY_B64=$(base64 -i "$KEYSTORE_FILE" | tr -d '\n')

echo ""
echo "Keystore cree : $KEYSTORE_FILE"
echo ""
echo "=== Copiez ces 4 valeurs dans GitHub > Settings > Secrets > Actions ==="
echo ""
echo "SIGNING_KEY (valeur longue en base64) :"
echo "$SIGNING_KEY_B64"
echo ""
echo "KEY_ALIAS : $KEY_ALIAS"
echo "KEY_STORE_PASSWORD : $KEY_STORE_PASSWORD"
echo "KEY_PASSWORD : $KEY_PASSWORD"
echo ""
echo "IMPORTANT : Sauvegardez le fichier $KEYSTORE_FILE et ces mots de passe !"
