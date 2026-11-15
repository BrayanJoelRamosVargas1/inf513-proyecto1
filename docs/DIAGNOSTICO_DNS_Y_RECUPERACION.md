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

## Ampliación: alternativas investigadas a las 11:02–11:05 de Bolivia

El usuario pidió buscar alternativas en Reddit, foros y repositorios. Se
consultaron esas fuentes y se contrastaron los mecanismos con documentación
primaria. Los resultados nuevos no recuperaron la conexión institucional:

| Alternativa | Comprobación y resultado |
| --- | --- |
| IPv4, IPv6 y diagnóstico de DNSSEC | Ocho consultas Google DoH: A/AAAA de db y mail con CD=0 y CD=1. Todas devolvieron SERVFAIL y servidor autoritativo sin respuesta. No se desactivó DNSSEC del equipo. |
| Cachés de otros resolutores | Doce consultas UDP: ambos nombres en Google, Cloudflare, Quad9, OpenDNS y dos DNS públicos del proveedor presentes en la configuración local. Google y Cloudflare devolvieron SERVFAIL; los otros cuatro no respondieron en 3 s. Un timeout no demuestra ausencia de datos en su caché. |
| Repositorios de la materia | Búsqueda de código GitHub y lectura de 50 archivos coincidentes, de 23 repositorios públicos. Se extrajeron solamente nombres/IP, sin utilizar credenciales de terceros. Apareció 37.187.122.232 como dbserver en Aliaga23/practica_u4_3/env.php. |
| DNS histórico | La API pública de mnemonic no encontró registros para db ni el dominio raíz; encontró el alias histórico mail → dns y el A histórico dns → 37.187.122.232, visto por última vez en febrero de 2024. No prueba que esa IP sea el destino actual. |
| Conectar a la IP antigua | Intentos TCP a 37.187.122.232:5432 y :443 agotaron 4 s sin conectar. No se enviaron contraseñas, ni se instaló una entrada hosts. |
| Túnel SSH | Otro proyecto de INF513 incluye abrir_tunel_bd.sh, que requiere autenticación SSH. Las cuentas de correo y PostgreSQL no acreditan ese acceso. Un intento TCP al DNS actual 139.99.68.169:22 tampoco conectó en 4 s; :995 y :465 tampoco. No se intentó iniciar sesión. |

Las veinte consultas adicionales quedan en
`evidencias/alternativas-dns-20261115.json`. Los resultados históricos y el
inventario de archivos consultados se conservaron localmente como diagnóstico.
Las pruebas TCP fueron conexiones puntuales, sin barridos de red.

Una IP vigente y confirmada por el operador permitiría ensayar acceso directo
si el servicio está disponible; un túnel requiere un servidor SSH accesible y
una cuenta autorizada. Ninguna de esas condiciones quedó satisfecha en esta
revisión. La restauración local descrita arriba sigue disponible para ensayar
la base, pero no sustituye recepción/envío de correo institucional.

Fuentes primarias consultadas:

- [Google: diagnóstico de dominios](https://developers.google.com/speed/public-dns/docs/troubleshooting/domains).
- [Google: parámetro CD de DoH](https://developers.google.com/speed/public-dns/docs/doh/json).
- [PostgreSQL: host y hostaddr](https://www.postgresql.org/docs/18/libpq-connect.html).
- [PostgreSQL: túneles SSH](https://www.postgresql.org/docs/current/ssh-tunnels.html).
- [mnemonic: API pública de DNS histórico](https://docs.mnemonic.no/service-integration-guides/passivedns/docs/public/01-public_api.html).
- [CIRCL/PyPDNS](https://github.com/CIRCL/PyPDNS) y [condiciones de acceso a CIRCL](https://new.circl.lu/services/passive-dns/); no se dispone de cuenta de ese servicio.
- [Proyecto INF513 con túnel](https://github.com/jhasmany-fernandez/F.I.C.C.T.-Primer-Parcial-INF513/blob/main/abrir_tunel_bd.sh).
