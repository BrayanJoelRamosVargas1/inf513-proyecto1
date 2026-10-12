# Pruebas realizadas

Fecha de comprobación: 12 de octubre de 2026.

## Automatizadas

- Compilación con Java 17 y Maven.
- Ejecución de las pruebas unitarias del intérprete de órdenes y sus validaciones.
- Generación correcta del JAR ejecutable.
- Aplicación del esquema `database/schema.sql` en la base `db_grupo09sa`.
- Caída simulada de PostgreSQL: el proceso permaneció activo y aplicó reintentos progresivos.
- Caída simulada de red y PostgreSQL: el proceso permaneció activo y limitó la frecuencia de reconexión.
- Lectura real por Roundcube HTTPS: autenticación y listado correcto de 149 mensajes sin imprimir su contenido.
- Fuzzing determinista intensivo: 10.000.000 de asuntos aleatorios o malformados sin derribar el analizador.
- Ráfaga concurrente local: 320.000 análisis distribuidos en 32 hilos sin errores ni estado compartido.
- Ejecución con memoria máxima de 64 MB y correo/base inaccesibles: el proceso permaneció activo, sin crecimiento agresivo de reintentos.
- Suite automatizada: 26 pruebas aprobadas, incluidas apropiación del primer DECANO, remitentes malformados, configuración fuera de rango, inmutabilidad de órdenes y mayúsculas obligatorias.
- Los filtros reales de LISUSU, LISCON, LISCAR y LISPOST se aplican con parámetros preparados y escape de `%`, `_` y `\`.
- La consulta de correos ya procesados se hace por lotes de hasta 1.000 IDs; el historial completo no se carga en memoria.
- Dependencias actualizadas: Jackson 2.22.3, PostgreSQL JDBC 42.7.14, Apache HttpClient 5.6.1 y Angus Mail 2.0.4. Las dos últimas corrigen avisos de seguridad publicados.
- Casos límite comprobados: letras en identificadores numéricos, `NaN`, puntajes fuera de rango, textos mayores que las columnas, caracteres de control y espacios accidentales.
- Restricciones defensivas aplicadas en PostgreSQL para fechas y puntajes, además de la validación Java.
- Diagnóstico remoto de integridad aprobado: las siete restricciones críticas y el índice único de correo sin distinción de mayúsculas están presentes en `db_grupo09sa`.
- Auditoría adversarial directa a PostgreSQL aprobada: rechazó fechas invertidas, estados inventados y correos duplicados por mayúsculas; toda la sesión fue revertida.
- Análisis estático SpotBugs: cero hallazgos de prioridad alta; el único aviso medio es la referencia interna obligatoria al `DataSource`.
- Análisis de dependencias y convergencia Maven aprobados. El análisis automático de CVE no pudo descargar su base NVD por una restricción de conexión local del entorno; no se presenta como prueba aprobada.
- Reinicio del servicio con recuperación de cola e idempotencia persistente.

## Integración real por correo

Se verificó el recorrido completo con mensajes enviados desde Gmail a la cuenta institucional del Grupo 09 SA:

1. Gmail envió la orden en el asunto.
2. El servicio Java la recuperó mediante Roundcube HTTPS.
3. La orden fue validada y procesada.
4. PostgreSQL fue consultado cuando correspondía.
5. El servicio respondió mediante el Roundcube institucional.
6. La respuesta llegó a Gmail.

Órdenes comprobadas de extremo a extremo:

- `AYUDA[]`: devolvió el catálogo de órdenes.
- `LISCON["*"]`: devolvió una respuesta de ejecución correcta y sin resultados cuando no había convocatorias registradas.

Durante estas pruebas Gmail colocó las respuestas institucionales en Spam. Eso no impidió la entrega, pero debe revisarse esa carpeta durante la defensa.

## Alcance de la evidencia

Los demás comandos están implementados, compilados y cubiertos por la estructura de base de datos y las validaciones del programa. No se afirma que todos hayan sido ejecutados de extremo a extremo con datos reales. La demostración completa debe realizarse con la secuencia indicada en `MANUAL_DEFENSA.md`.
