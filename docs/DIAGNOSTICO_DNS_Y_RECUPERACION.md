# Diagnóstico de servicios y recuperación — 15 de noviembre de 2026

La conexión institucional sigue pendiente. Las cuentas de correo y PostgreSQL
del grupo no permiten administrar el DNS de tecnoweb.org.bo.

## Evidencia de red

Se ejecutó `python scripts/diagnosticar-servicios.py`. El resultado completo está
en `evidencias/diagnostico-dns-20261115.json` (07:27 UTC). El sistema local no
resolvió db.tecnoweb.org.bo ni mail.tecnoweb.org.bo. Google y Cloudflare devolvieron
SERVFAIL para ambos servicios y la consulta NS del dominio. Google informó
`Name servers did not respond [139.99.68.169]`; Cloudflare informó
`No Reachable Authority at delegation tecnoweb.org.bo`. El control github.com
resolvió correctamente por las tres vías.

Además, una consulta directa NS al servidor de .bo 204.61.216.48 devolvió una
delegación con un único servidor dns.tecnoweb.org.bo y glue 139.99.68.169.
Las consultas DNS directas a esa dirección agotaron el tiempo de espera por
UDP y TCP. Esto identifica un fallo de disponibilidad de resolución observado
desde este equipo y los dos resolutores públicos; no demuestra por sí solo que
PostgreSQL y correo estén apagados. La IP del DNS no se ha considerado una IP
confirmada de las aplicaciones, y no se enviaron credenciales a IP supuestas.

No se modificaron hosts, DNS de Windows, delegaciones ni configuración remota.
El script solo consulta nombres públicos; puede repetirse desde Fedora con
Python 3 y no requiere claves. Los resultados son una observación fechada,
no una supervisión continua. El administrador de infraestructura debe revisar
la disponibilidad del DNS autoritativo, su conectividad y la redundancia de
la delegación. Con las cuentas del grupo no se puede aplicar esa reparación.
El servicio del proyecto ya reintenta las conexiones automáticamente.

## Recuperación aislada comprobada

Se restauró el respaldo privado `respaldo-antes-categorias-20261114220047.sql`
en una base nueva de PostgreSQL 15, únicamente en 127.0.0.1:15439. La restauración
y la aplicación transaccional del `database/schema.sql` actual terminaron sin
errores. Resultado: 19 tablas p1_, 3 usuarios, 0 postulaciones. PostgreSQL local
se detuvo al finalizar. No se inició el consumidor de correo ni se enviaron
mensajes durante esta prueba.

Este respaldo del 14 de noviembre contiene las 16 tablas p1_ previas a la
migración de categorías; el esquema actual agrega las restantes. No incluye
una copia de persona ni garantiza datos posteriores a su fecha. No sustituye
un respaldo actual del servidor ni acredita que el servicio institucional
funcione. El SQL y los registros con datos privados permanecen fuera de Git.

## Alcance de esta entrega

Este cambio añade diagnóstico y evidencia, sin modificar las reglas de los
8 casos de uso ni el JAR. Las 122 pruebas de la revisión anterior se conservan;
la integración continua vuelve a ejecutar la suite sobre este cambio.
