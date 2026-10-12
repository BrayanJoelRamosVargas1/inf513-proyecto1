# Pruebas realizadas

Fecha de comprobación: 12 de octubre de 2026.

## Automatizadas

- Compilación con Java 17 y Maven.
- Ejecución de las pruebas unitarias del intérprete de órdenes y sus validaciones.
- Generación correcta del JAR ejecutable.
- Aplicación del esquema `database/schema.sql` en la base `db_grupo09sa`.
- Trece pruebas automatizadas sin fallos.
- Caída simulada de PostgreSQL: el proceso permaneció activo y aplicó reintentos progresivos.
- Caída simulada de POP3: el proceso permaneció activo y limitó la frecuencia de reconexión.
- Reinicio del servicio con recuperación de cola e idempotencia persistente.

## Integración real por correo

Se verificó el recorrido completo con mensajes enviados desde Gmail a la cuenta institucional del Grupo 09 SA:

1. Gmail envió la orden en el asunto.
2. El servicio Java la recuperó por POP3.
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
