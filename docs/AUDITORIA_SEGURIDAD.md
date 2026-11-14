# Auditoría del Proyecto 1, Grupo 09 SA

Fecha: 14 de noviembre de 2026. Alcance: los ocho casos aprobados, sin incorporar la ampliación de 30 requisitos. Se revisaron el servicio Java, el analizador, SQL, autorización, confirmaciones, correo, adjuntos, configuración, dependencias y archivos publicados. Es una auditoría del proyecto y pruebas aisladas; no un examen de penetración de los servidores de la universidad.

## Estado real de publicación y funcionamiento

El código de esta revisión se publica en la rama `codex/mejoras-email-puds` y en la [propuesta de cambios #1](https://github.com/BrayanJoelRamosVargas1/inf513-proyecto1/pull/1). La rama `main` no se ha modificado. La actividad no se ha enviado a Presencial. No se ha desplegado esta revisión en la infraestructura institucional: faltan respaldo, migración y ensayo completo con correo real. La autenticación institucional comprobada anteriormente no demuestra ese despliegue.

## Problemas encontrados y corregidos

| Hallazgo | Corrección y comprobación |
|---|---|
| HttpClient 5.6.1 y HttpCore 5.4 figuraban en tres avisos de agotamiento de conexiones o memoria | HttpClient 5.6.5 resuelve HttpCore 5.4.4. OSV se consultó nuevamente sobre las 23 dependencias resueltas, incluidas las transitivas y de pruebas: cero coincidencias en esa consulta. |
| POP3 ignoraba la opción SSL | Ahora inicia TLS, valida el nombre del servidor y no autentica si falla el handshake. POP3 remoto sin TLS se rechaza; el modo predeterminado sigue siendo Roundcube HTTPS. La prueba observa un registro TLS, en lugar de USER/PASS, ante un servidor local falso. |
| La línea POP3 se limitaba después de cargarla entera | Lectura limitada a 16.384 caracteres recibidos, incluidos retornos de carro, y cierre del socket cuando falla la conexión o autenticación. Prueba con línea hostil sin terminación. |
| La respuesta de error POP3 se copiaba literalmente | El error ahora es genérico y no refleja contenido arbitrario del servidor. |
| IMAP sin SSL y SMTP autenticado sin SSL podían enviar credenciales sin exigir cifrado | Se exige STARTTLS en esos modos. La comprobación de identidad TLS de Jakarta Mail permanece habilitada. La infraestructura que no ofrezca TLS debe usar el adaptador HTTPS. |
| Roundcube podía seguir redirecciones fuera del servidor previsto | Solo admite el mismo origen HTTPS, con el mismo host y puerto, sin credenciales en la URL. Pruebas de cambio de dominio, puerto, esquema y usuario. |
| La validación HTTPS solo contemplaba la salida Roundcube | También se exige cuando Roundcube se usa únicamente para recibir. |
| El registro AppConfig podía mostrar claves con su toString generado | Representación explícita sin credenciales, y rechazo de controles en usuario/clave para impedir inyección de comandos POP3 desde configuración. |
| Un requisito corregido conservaba un rechazo histórico que impedía habilitar | Se considera la última evaluación de cada requisito por evaluador, conservando todas las filas del historial. Prueba de rechazo, subsanación y habilitación. |
| Cambiar el estado de un ganador infringía la restricción entre estado y plaza | Reafirmar su habilitación conserva GANADOR; inhabilitarlo libera la plaza dentro de la transacción auditada. Prueba con PostgreSQL real aislado. |

Los avisos de bibliotecas se documentan en `evidencias/auditoria-osv-antes.json`. Referencias: [HttpClient: GHSA-hjcp-jmpx-g3qm](https://osv.dev/vulnerability/GHSA-hjcp-jmpx-g3qm), [HttpCore: GHSA-hf6x-8p5f-cgmf](https://osv.dev/vulnerability/GHSA-hf6x-8p5f-cgmf), [HTTP/2: GHSA-v3jc-474w-2wm6](https://osv.dev/vulnerability/GHSA-v3jc-474w-2wm6) y [versiones publicadas por Apache](https://hc.apache.org/news.html). Una biblioteca afectada no implica que se haya explotado el sistema; se actualiza para retirar la dependencia vulnerable.

## Herramientas y evidencia

- **Maven/JUnit, Java 17 y PostgreSQL 15 aislado:** 78 pruebas, cero fallos, errores u omisiones. Incluyen recorrido de los ocho CU, permisos y revocación, tokens de confirmación, reintentos, duplicados, transacciones, concurrencia, inyección SQL, validación de adjuntos y seis pruebas adicionales de transporte. Resultados: `evidencias/pruebas-locales.json` y `evidencias/maven-verificacion.txt`.
- **OSV API:** 23 coordenadas de dependencias Maven, sin transmitir código ni secretos. Evidencia final: `evidencias/auditoria-osv.json`. Excluye plugins de construcción, sistema operativo, servidor PostgreSQL y Roundcube. No se interpreta como garantía de ausencia de vulnerabilidades desconocidas.
- **SpotBugs 4.10.5.0 y Find Security Bugs 1.14.0:** análisis con esfuerzo máximo y umbral bajo, sin filtros de supresión. El informe XML conserva las alertas para revisión. Compilar o terminar el análisis correctamente no significa que no existan alertas.
- **Gitleaks 8.30.1:** distribución oficial con SHA-256 contrastado con su archivo de sumas. Historial de todas las referencias locales y archivos actuales, con resultados redactados. Cero detecciones finales. La distribución externa del propio auditor contenía dos ejemplos que activaban reglas; se excluye únicamente `tools/` y los productos de compilación en `target/`, no el código ni la configuración del proyecto. Gitleaks no garantiza detectar cualquier contraseña humana ni examina el interior de binarios o documentos Office como código fuente.

## Revisión de alertas estáticas

El detalle exacto y sus ubicaciones se conservan en `evidencias/spotbugs-final.xml` y el resumen en `evidencias/spotbugs-resumen.json`. No se borran las alertas para aparentar un informe limpio.

| Categoría | Evaluación del código |
|---|---|
| SQL_INJECTION_JDBC y SQL generado | Las consultas de negocio reciben valores mediante PreparedStatement. Los marcadores IN se generan según la cantidad acotada de elementos; los cambios de estructura son fragmentos constantes. `AplicarEsquema` ejecuta un archivo SQL local de administración, no un correo. No se identificó un trayecto desde parámetros del correo hasta SQL concatenado. |
| UNENCRYPTED_SOCKET | La rama TCP sin TLS se conserva para pruebas POP3 en loopback; AppConfig rechaza esa configuración para hosts remotos. La rama SSL valida identidad. La alerta no conoce esa condición entre clases. |
| PREDICTABLE_RANDOM | ThreadLocalRandom se usa únicamente para espaciar reintentos. Las confirmaciones usan 32 bytes de SecureRandom y hashes SHA-256. |
| IMPROPER_UNICODE | Comparaciones de identificadores de protocolos, tokens fijos y correos. Los protocolos se normalizan con Locale.ROOT y lista permitida; Main acepta remitentes ASCII y exige confirmación para comandos protegidos. No se encontró una evasión de rol mediante esas comparaciones. |
| DE_MIGHT_IGNORE | La alternativa de decodificación de Roundcube conserva un texto que ya puede estar decodificado; el analizador de comandos y sus límites se aplican después. |
| DM_CONVERT_CASE | Conversión usada para detectar el marcador técnico de error del servidor; no concede permisos. |
| REC_CATCH_EXCEPTION y THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION | Advertencias de diseño: fronteras de reintento, validación y contratos amplios. Se verificó que fallos de negocio/auditoría revierten la transacción; no se convierten en éxito. |
| RCN_REDUNDANT_NULLCHECK, SIC_INNER_SHOULD_BE_STATIC_ANON y EI_EXPOSE_REP2 | Mantenibilidad y referencia intencional al DataSource administrado. No son entradas remotas ni exposiciones de claves. |

## Límites y pendientes

1. **Despliegue institucional sin verificar.** La revisión automática rechazó el intento previo de crear el respaldo, sin motivo específico. No se omitió ese paso ni se modificó la base institucional para esta auditoría. Hace falta un respaldo válido antes de migrar y un recorrido de éxito/error/duplicado/PDF con las cuentas reales.
2. **Datos de la demostración:** INSPER/LISPER son públicos por el ejemplo literal de la consigna. La tabla `persona` debe contener datos ficticios; no usarla para datos personales reales. Los usuarios con rol se almacenan en `p1_usuario` y las operaciones protegidas requieren confirmación.
3. **Correo real y configuración:** falta comprobar los modos alternativos contra servidores reales, la aceptación docente de Roundcube HTTPS y el cifrado/verificación de certificados de PostgreSQL en el entorno institucional. La URL JDBC heredada no exige `sslmode=verify-full`; este requisito depende del certificado y de la configuración de esa infraestructura.
4. **PDF:** se comprueba cabecera, nombre, tamaño y SHA-256. No hay antivirus ni validación estructural completa; el servicio no ejecuta ni abre el documento. Las referencias externas solo se guardan, no se descargan.
5. **Disponibilidad:** hay cola, límites y reintentos; siguen siendo necesarios control de espacio, retención de correos/adjuntos y monitoreo operativo. No se hicieron ataques de carga al servidor universitario.
6. **Reglas académicas documentadas:** ponderación fija 40/60, desempate por ID y promedio de entradas de examen. Esta auditoría no inventa un reglamento ni incorpora nuevos CU. La carga de una nueva nota añade una entrada; no sustituye una anterior.

## Reproducir

Con una base PostgreSQL de pruebas aislada configurada mediante TEST_DB_URL, TEST_DB_USER y TEST_DB_PASSWORD:

```text
mvn --batch-mode --no-transfer-progress clean verify
mvn --batch-mode --no-transfer-progress -Pauditoria spotbugs:spotbugs
mvn dependency:tree -DoutputType=json -DoutputFile=target/dependencias-auditoria.json
python scripts/auditar-dependencias.py
gitleaks git --redact --log-opts=--all --report-format json --report-path evidencias/gitleaks-historial.json
gitleaks dir . --config .gitleaks.toml --redact --report-format json --report-path evidencias/gitleaks-archivos.json
```

La base de pruebas debe ser descartable: los ensayos crean y eliminan esquemas temporales. No ejecutar las pruebas con la cuenta institucional. El perfil de auditoría es opcional para uso local y se ejecuta también en CI; OSV falla si informa vulnerabilidades o no se puede completar la consulta.
