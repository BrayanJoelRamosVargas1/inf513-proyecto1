# Verificación adicional de inyección SQL

Fecha: 14 de noviembre de 2026. Alcance: servicio Java del Proyecto 1 y PostgreSQL 15 local desechable; no se atacaron los servidores institucionales.

## Resultado funcional observado

La suite completa terminó con **103 pruebas, cero fallos, errores y omisiones**. Se añadieron tres pruebas en `InyeccionSqlPostgresTest`, cada una con su propio esquema temporal:

1. Diez cadenas con comillas, comentarios, UNION, instrucciones apiladas, DROP TABLE, pg_sleep y barras se insertan como nombres mediante INSPER. Se verifica que el valor almacenado coincide literalmente, que permanecen todos los registros y que una operación normal posterior sigue funcionando.
2. Los filtros con `%`, `_` y barra invertida solo recuperan los registros que contienen literalmente esos caracteres. Las diez cadenas SQL no convierten LISPER en una consulta general ni revelan los registros ficticios ajenos al filtro.
3. Esas cadenas no eluden la autorización del remitente, se conservan como datos en los identificadores de la cola y son rechazadas como identificadores numéricos de GENRAN y REPEST. El usuario administrador ficticio sigue intacto.

Son ensayos locales con datos ficticios; no se enviaron estos intentos por correo institucional. La prueba con `pg_sleep` comprueba conservación literal, no estima seguridad mediante diferencias de tiempo.

## Revisión de las consultas

Se revisaron las consultas de `RepositorioPostgres`, el bloqueo de instancia en `Main` y las utilidades administrativas. Los valores recibidos por correo se vinculan con `PreparedStatement`; no se identificó un recorrido desde esos valores hasta texto SQL concatenado. La estructura variable observada consiste en:

- Marcadores `?` para lotes de hasta 1000 identificadores. Los valores de esos identificadores se vinculan después.
- Selección entre consultas constantes y un fragmento constante `for update` según controles internos.
- Lectura y ejecución de `database/schema.sql` por la herramienta local `AplicarEsquema`, que no se invoca a partir de un asunto de correo.

La parametrización se corresponde con la [guía de prevención de OWASP](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html). La revisión y las pruebas apoyan la conclusión **«no se reprodujo inyección SQL en las rutas ensayadas»**, sin convertirla en una garantía universal.

## Otros controles revisados

La suite también cubre permisos y revocación, confirmaciones vinculadas al remitente, caducidad y reutilización de tokens, duplicados, concurrencia, reversión transaccional, MIME malformado, límites de adjuntos, nombres con rutas y fallo del antivirus. La salida del correo se envía como texto plano; Roundcube exige HTTPS y limita redirecciones al mismo origen. El antivirus usa una lista de argumentos sin shell y no recibe las claves del correo ni de PostgreSQL.

La búsqueda Gitleaks en archivos de trabajo y 30 commits locales terminó sin coincidencias. Esto no demuestra que no existan secretos en archivos externos al repositorio, respaldos privados, otros repositorios o servicios institucionales.

OSV se volvió a consultar sobre las 23 dependencias Maven resueltas y no reportó vulnerabilidades conocidas en esa consulta. SpotBugs con Find Security Bugs se ejecutó nuevamente: conserva 68 advertencias, sin errores de análisis ni clases faltantes. Sus categorías y cantidades coinciden con la revisión anterior; se revisaron nuevamente los trayectos relacionados con SQL y ejecución de comandos, sin identificar una entrada de correo que se ejecute como SQL o shell. Las advertencias siguen disponibles, no se presenta el análisis como un informe de cero alertas.

Evidencia: `evidencias/verificacion-inyeccion-sql.json`, `evidencias/maven-verificacion-inyeccion-sql.txt`, `evidencias/auditoria-osv.json`, `evidencias/spotbugs-final.xml` y los informes Gitleaks. La clasificación estática previa se conserva en `AUDITORIA_SEGURIDAD.md`.

## Límites que siguen abiertos

- PostgreSQL institucional continúa sin TLS. No se declara solucionado desde el cliente.
- INSPER y LISPER son públicos para los ejemplos de la actividad; persona debe contener datos ficticios, no expedientes personales reales.
- No se ejecutó una prueba de penetración del Roundcube ni del servidor PostgreSQL universitario, ni una prueba destructiva de carga contra ellos.
- Antivirus y límites de tamaño no garantizan que cualquier PDF sea inocuo; no hay desarme de contenido ni validación estructural completa.
- Persisten diferencias funcionales de ponderaciones por área, categorías/cupos, empates y reportes detectadas en el contraste con la propuesta del grupo. Que las pruebas de seguridad pasen no acredita esas funciones pendientes.
- Fedora nativo y el recorrido real completo desde cada rol todavía requieren verificación. La prueba de Linux en CI no sustituye el ensayo en ese equipo.

El PUDS y los comprimidos de la revisión anterior registran la suite de 100 pruebas; esta ampliación añade evidencia de seguridad y no anuncia una nueva entrega académica completa.

## Reproducir

Usar exclusivamente una base de ensayo con permiso de crear y eliminar esquemas. Configurar TEST_DB_URL, TEST_DB_USER y TEST_DB_PASSWORD, y ejecutar:

```text
mvn -Dtest=InyeccionSqlPostgresTest test
mvn test
```

Las pruebas crean esquemas con un nombre aleatorio generado internamente y los retiran al terminar. No se deben configurar con la base institucional del grupo.
