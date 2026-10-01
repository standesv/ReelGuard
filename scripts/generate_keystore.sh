#!/bin/bash
# Exécuter ce script UNE SEULE FOIS sur votre ordinateur pour créer votre keystore de signature.
# Les valeurs générées seront à copier dans les Secrets GitHub.

set -e

# Nom de fichier NEUF : ne jamais reutiliser un keystore existant, sinon keytool tente
# de l'ouvrir avec le nouveau mot de passe et echoue ("keystore password was incorrect").
KEYSTORE_FILE="reelguard-upload.jks"
KEY_ALIAS="reelguard-key"
# UN SEUL mot de passe : les keystores PKCS12 (format par defaut de keytool) ne
# supportent PAS un mot de passe de cle different du mot de passe du keystore.
# Utiliser deux valeurs differentes fait echouer la signature cote Gradle
# ("Get Key failed: Given final block not properly padded"). On garde donc store = key.
KEY_STORE_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=')
KEY_PASSWORD="$KEY_STORE_PASSWORD"

# Securite : refuser d'ecraser un keystore existant (il aurait un autre mot de passe).
if [ -f "$KEYSTORE_FILE" ]; then
  echo "ERREUR : $KEYSTORE_FILE existe deja. Renomme-le ou supprime-le avant de relancer."
  exit 1
fi

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

# Sauvegarde des identifiants dans un fichier LOCAL (ignore par git) pour ne jamais
# les reperdre. A copier dans un gestionnaire de mots de passe, puis a supprimer.
CREDS_FILE="keystore-credentials.txt"
{
  echo "# ReelGuard - identifiants de signature (NE JAMAIS COMMITTER / partager)"
  echo "# Genere le : $(date)"
  echo "# Certificat SHA1 :"
  keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEY_STORE_PASSWORD" 2>/dev/null | grep -i "SHA1:" | head -1
  echo ""
  echo "KEY_ALIAS=$KEY_ALIAS"
  echo "KEY_STORE_PASSWORD=$KEY_STORE_PASSWORD"
  echo "KEY_PASSWORD=$KEY_PASSWORD"
  echo ""
  echo "SIGNING_KEY (base64) ="
  echo "$SIGNING_KEY_B64"
} > "$CREDS_FILE"

# Export du certificat .pem (utile pour une reinitialisation de cle d'import Play Console)
keytool -export -rfc -keystore "$KEYSTORE_FILE" -alias "$KEY_ALIAS" \
  -storepass "$KEY_STORE_PASSWORD" -file upload_certificate.pem 2>/dev/null

echo ""
echo "Keystore cree : $KEYSTORE_FILE"
echo "Identifiants sauvegardes dans : $CREDS_FILE  (ignore par git)"
echo "Certificat exporte dans       : upload_certificate.pem"
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
echo "IMPORTANT : copiez $CREDS_FILE dans un gestionnaire de mots de passe,"
echo "sauvegardez $KEYSTORE_FILE hors du projet, puis supprimez $CREDS_FILE."
