#!/usr/bin/env sh
set -u
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAR="$BASE_DIR/target/inf513-proyecto1-1.0.0.jar"
[ -f "$JAR" ] || JAR="$BASE_DIR/dist/inf513-proyecto1-1.0.0.jar"
[ -f "$JAR" ] || { printf 'No se encontró el JAR. Ejecute mvn clean package o restaure dist/.\n' >&2; exit 2; }
: "${MAIL_PASSWORD:?Defina MAIL_PASSWORD antes de iniciar}"
: "${DB_PASSWORD:?Defina DB_PASSWORD antes de iniciar}"
while true; do
  java -jar "$JAR"
  code=$?
  [ "$code" -eq 0 ] && exit 0
  printf 'El servicio terminó con código %s. Reinicio en 5 segundos.\n' "$code" >&2
  sleep 5
done
