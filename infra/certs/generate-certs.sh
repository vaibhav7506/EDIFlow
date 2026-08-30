#!/usr/bin/env bash
set -euo pipefail

CERT_DIR=/certs
PASSWORD=${TLS_STORE_PASSWORD:-changeit}
GATEWAY_PUBLIC_SAN=${GATEWAY_PUBLIC_SAN:-}
mkdir -p "$CERT_DIR"

export_client_pem() {
  local name=$1
  openssl pkcs12 -in "$CERT_DIR/$name.p12" -passin "pass:$PASSWORD" -clcerts -nokeys -out "$CERT_DIR/$name.crt.pem"
  openssl pkcs12 -in "$CERT_DIR/$name.p12" -passin "pass:$PASSWORD" -nocerts -nodes -out "$CERT_DIR/$name.key.pem"
}

if [[ -f "$CERT_DIR/gateway.p12" && -f "$CERT_DIR/partner-client.p12" && -f "$CERT_DIR/dashboard-client.p12" && -f "$CERT_DIR/truststore.p12" ]]; then
  export_client_pem partner-client
  export_client_pem dashboard-client
  chmod 644 "$CERT_DIR"/*
  exit 0
fi

keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -dname "CN=EDIFlow Development CA,O=EDIFlow" -ext bc=ca:true -validity 3650 -keystore "$CERT_DIR/ca.p12" -storetype PKCS12 -storepass "$PASSWORD" -keypass "$PASSWORD" -noprompt
keytool -exportcert -alias ca -keystore "$CERT_DIR/ca.p12" -storepass "$PASSWORD" -rfc -file "$CERT_DIR/ca.crt"

issue_certificate() {
  local name=$1
  local subject=$2
  local eku=$3
  local san=$4
  keytool -genkeypair -alias "$name" -keyalg RSA -keysize 2048 -dname "$subject" -validity 825 -keystore "$CERT_DIR/$name.p12" -storetype PKCS12 -storepass "$PASSWORD" -keypass "$PASSWORD" -ext "SAN=$san" -noprompt
  keytool -certreq -alias "$name" -keystore "$CERT_DIR/$name.p12" -storepass "$PASSWORD" -file "$CERT_DIR/$name.csr" -ext "SAN=$san"
  keytool -gencert -alias ca -keystore "$CERT_DIR/ca.p12" -storepass "$PASSWORD" -infile "$CERT_DIR/$name.csr" -outfile "$CERT_DIR/$name.crt" -rfc -validity 825 -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=$eku" -ext "SAN=$san"
  keytool -importcert -alias ca -keystore "$CERT_DIR/$name.p12" -storepass "$PASSWORD" -file "$CERT_DIR/ca.crt" -noprompt
  keytool -importcert -alias "$name" -keystore "$CERT_DIR/$name.p12" -storepass "$PASSWORD" -file "$CERT_DIR/$name.crt" -noprompt
}

gateway_san="dns:gateway,dns:localhost,ip:127.0.0.1"
if [[ -n "$GATEWAY_PUBLIC_SAN" ]]; then
  gateway_san="$gateway_san,$GATEWAY_PUBLIC_SAN"
fi
issue_certificate gateway "CN=gateway,O=EDIFlow" serverAuth "$gateway_san"
issue_certificate partner-client "CN=partner-client,OU=B2B Partners,O=EDIFlow" clientAuth "dns:partner-client"
issue_certificate dashboard-client "CN=dashboard-client,OU=Operations,O=EDIFlow" clientAuth "dns:dashboard-client"

keytool -importcert -alias ediflow-ca -keystore "$CERT_DIR/truststore.p12" -storetype PKCS12 -storepass "$PASSWORD" -file "$CERT_DIR/ca.crt" -noprompt
export_client_pem dashboard-client
export_client_pem partner-client
chmod 644 "$CERT_DIR"/*
