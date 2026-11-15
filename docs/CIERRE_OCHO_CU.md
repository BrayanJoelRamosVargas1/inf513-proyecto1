# Cierre funcional y de seguridad de los ocho casos aprobados

Revisión del 14 de noviembre de 2026. El alcance permanece en CU1 a CU8 aprobados en el foro del Grupo 09. Las nuevas operaciones completan esos casos; no se añaden los casos del Proyecto 2. Los 30 requisitos del planteamiento son requisitos dentro de esos ocho CU.

## Correcciones de negocio

- **CU2:** oferta y cupo por cargo mediante ASIOFE. La suma de cupos no supera el total de la convocatoria, incluso ante dos configuraciones concurrentes. Una oferta de cero plazas impide nuevas postulaciones a ese cargo.
- **CU5:** áreas de examen y pesos por convocatoria y cargo con CFGARE; los pesos deben sumar exactamente 100 antes de registrar notas por REGAREA. Se promedian las últimas notas de cada evaluador por área y luego se aplican los pesos. No se modifican pesos una vez registrado un examen. REGEXA permanece para categorías sin áreas configuradas y no permite eludirlas.
- **CU6:** ranking independiente por cargo y asignación según su cupo. Un empate que cruza el límite de plazas exige una resolución motivada de la autoridad con DESEMP. El identificador solo estabiliza la presentación; no decide el ganador. Corregir una nota invalida la resolución de la postulación, aunque el puntaje numérico quede igual.
- **CU7:** las operaciones anteriores requieren los permisos y la confirmación existentes y dejan auditoría. La resolución registra actor, orden completo de candidatos y justificación. Se bloquean cambios de evaluación mientras existen plazas asignadas en la convocatoria.
- **CU8:** REPEST incluye estado, documentos, notas, aportes del 40/60, resultado final, ranking, empates y vacantes por cargo. LISCFG permite revisar cupos y ponderaciones.

La combinación de méritos 40% y examen 60% procede del planteamiento del grupo. No se presenta como una normativa oficial adicional del docente. Dentro del examen, los pesos de las áreas se configuran con CFGARE.

## Operaciones añadidas al catálogo

Los identificadores son ejemplos; sustituirlos por registros de ensayo válidos. Los comandos de escritura requieren responder la confirmación enviada por el sistema.

| CU | Operación | Parámetros en orden | Rol |
|---|---|---|---|
| 2 | ASIOFE | convocatoria, cargo, plazas | DECANO o VICEDECANO |
| 2 y 5 | CFGARE | convocatoria, cargo, área, peso porcentual | DECANO o VICEDECANO |
| 2 y 8 | LISCFG | convocatoria | DECANO o VICEDECANO |
| 5 | REGAREA | postulación, área, nota de 0 a 100, observación | EVALUADOR asignado |
| 6 y 7 | DESEMP | convocatoria, cargo, IDs ordenados separados por coma, justificación de al menos 10 caracteres | DECANO o VICEDECANO |

```text
ASIOFE["1","1","2"]
CFGARE["1","1","teoria","30"]
CFGARE["1","1","practica","70"]
LISCFG["1"]
REGAREA["1","teoria","50","Evaluación teórica"]
REGAREA["1","practica","90","Evaluación práctica"]
DESEMP["1","1","2,1","Resolución motivada del tribunal de evaluación"]
```

DESEMP debe incluir exactamente todos los candidatos elegibles del mismo empate y cargo; no admite duplicados ni mezclas de categorías. El evaluador debe estar asignado al área indicada o al examen en general. Para las categorías con áreas, se necesitan todas las áreas y méritos antes de asignar una plaza. El ejemplo 30% de 50 y 70% de 90 produce examen 78; con méritos 80, el final es 78,80.

## Migración y compatibilidad

`database/schema.sql` añade las tablas p1_oferta, p1_area_examen y p1_desempate, la columna area de p1_examen y evaluacion_version de p1_postulacion. Son 19 tablas p1_ y la tabla independiente persona. No borra ni recalifica los datos previos. Las notas anteriores quedan en el área general.

Una convocatoria anterior con un solo cargo puede continuar usando su cupo global. Si tiene varios cargos, se exige configurar ASIOFE antes de adjudicar plazas; evita reutilizar el cupo global para cada cargo. La migración debe aplicarse antes de arrancar este JAR, con respaldo restaurado en una base de ensayo y el servicio detenido.

## Validación y auditoría

La suite completa aprobó **111 pruebas sin fallos, errores ni omisiones** sobre PostgreSQL 15 local desechable y Java 17. Incluye ocho pruebas nuevas de categorías, concurrencia de cupos, empate en el corte, invalidación de resoluciones, ponderaciones completas, permisos por área, cargos ofertados y almacenamiento literal de un nombre de área con caracteres SQL. Se conservan las pruebas de inyección SQL, confirmaciones, revocación, MIME, límites, PDF y reversión.

Las entradas se vinculan como datos con PreparedStatement; las concatenaciones de consultas nuevas contienen fragmentos constantes del programa. Esto sigue la [prevención de inyección SQL de OWASP](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html). No se reprodujo inyección SQL en las rutas ensayadas. No equivale a una demostración de ausencia de cualquier vulnerabilidad.

SpotBugs conserva **73 advertencias**, sin errores de análisis ni clases faltantes. Las advertencias SQL nuevas corresponden a GestionCategorias.preparar y al paso de consultas constantes de configuración y reportes al ejecutor del repositorio. Se revisaron sus llamadas: los valores del correo se pasan por separado a setString; ninguno forma el texto SQL. Se corrigió el cierre del PreparedStatement ante una excepción durante su configuración. La nueva advertencia EI_EXPOSE_REP2 señala el DataSource recibido por el constructor del repositorio: es una dependencia de infraestructura compartida, creada por el arranque; no se construye ni se reemplaza desde una orden por correo. Se conserva sin suprimir junto a las demás advertencias. Las cifras y categorías vigentes están en evidencias/spotbugs-resumen.json; el XML permite revisar cada señalamiento. La clasificación de alertas anteriores está en AUDITORIA_SEGURIDAD.md.

OSV no reportó coincidencias conocidas en las 23 dependencias Maven consultadas. Gitleaks no encontró secretos en los archivos de trabajo revisados. Estas herramientas tienen un alcance limitado y no certifican que el sistema sea invulnerable.

Evidencias reproducibles: evidencias/verificacion-categorias.json, evidencias/maven-verificacion-categorias.txt, evidencias/auditoria-osv.json y evidencias/spotbugs-final.xml. Ejecutar `mvn test` con TEST_DB_URL, TEST_DB_USER y TEST_DB_PASSWORD apuntando exclusivamente a una base de ensayo, nunca a la base compartida institucional.

## Pendientes externos y límites

- El PostgreSQL institucional no ofrece TLS; requiere intervención del administrador. El HTTPS del correo no cifra esa conexión independiente.
- El arranque, ClamAV, recepción, confirmación y respuesta después de reiniciar **Fedora nativo** deben ensayarse en ese sistema. Windows y CI Linux no sustituyen esa comprobación.
- No se ha realizado una prueba destructiva ni de penetración contra la infraestructura universitaria. Los ensayos adversariales utilizan datos ficticios y entornos locales.
- LISPER e INSPER conservan el comportamiento público de los ejemplos del docente; persona debe contener datos ficticios. Antivirus y límites no garantizan inocuidad absoluta de cada PDF.
- Las actividades de seguridad de Presencial que no pudieron abrirse no se consideran revisadas íntegramente. El texto visible del Proyecto 1 no acredita que SSL sea una exigencia explícita de esa entrega.

Esta revisión sustituye las afirmaciones de pendientes de categorías, áreas, empates y reportes de VERIFICACION_INYECCION_SQL.md. No sustituye el ensayo final en Fedora ni autoriza afirmar «cero vulnerabilidades».
