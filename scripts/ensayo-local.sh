#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
contenedor=inf513-ensayo-09sa
command -v podman >/dev/null || { echo 'Se requiere Podman para iniciar PostgreSQL local.' >&2; exit 1; }
if podman container exists "$contenedor"; then
    if [[ $(podman inspect -f '{{.State.Running}}' "$contenedor") != true ]]; then podman start "$contenedor" >/dev/null; fi
else
    podman run -d --name "$contenedor" -p 127.0.0.1:55439:5432 \
        -e POSTGRES_USER=ensayo -e POSTGRES_PASSWORD=ensayo-local -e POSTGRES_DB=ensayo \
        docker.io/library/postgres:15-alpine >/dev/null
fi
for intento in {1..20}; do
    if podman exec "$contenedor" pg_isready -q -h 127.0.0.1 -U ensayo -d ensayo; then
        exec java -cp 'dist/ensayo-local.jar:dist/inf513-proyecto1-1.0.0.jar' bo.edu.uagrm.inf513.grupo09sa.EnsayoManual
    fi
    sleep 1
done
echo 'PostgreSQL de ensayo no quedó disponible.' >&2
exit 1
