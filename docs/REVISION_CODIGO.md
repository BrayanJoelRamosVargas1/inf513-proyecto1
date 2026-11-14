# Revisión del código contra la consigna y la referencia

Auditoría posterior de esta misma primera parte: **79 pruebas aprobadas**. Véase [el informe de seguridad](AUDITORIA_SEGURIDAD.md), con las correcciones y límites actuales. Las cifras de 70 o 50 que siguen corresponden a revisiones anteriores.

Revisión del 14 de noviembre de 2026. Se contrastaron el texto de observaciones recibido, la actividad de Presencial identificada en `CONSIGNA_Y_TRAZABILIDAD.md`, los ocho casos aprobados y el código de BarberSeven del Grupo 05. La referencia orienta la separación de correo, analizador, validación de negocio y persistencia; sus reglas de citas y ventas no corresponden a auxiliaturas y no se trasladan al dominio del Grupo 09.

## Recorridos comprobados

| Caso | Implementación y comprobación |
|---|---|
| CU1 Usuarios | INSUSU/LISUSU, roles activos, Vicedecano como autoridad y alta inicial restringida; confirmación del remitente y revocación de permisos comprobadas. |
| CU2 Convocatorias | INSCON/LISCON e INSCAR/LISCAR; estados, fechas, cupos y cargo activo. |
| CU3 Postulación múltiple | Unicidad por convocatoria, postulante y cargo; segunda postulación a otro cargo; PDF durable antes de confirmar y asociación al correo original; reversión si falta el adjunto. |
| CU4 Requisitos y examen | Evaluador asignado, revisión de requisitos y HABPOS; REGEXA rechaza postulaciones no habilitadas. |
| CU5 Méritos y competencia | Puntajes entre 0 y 100, suma de méritos limitada a 100, promedio de exámenes y ponderación fija 40/60. Notas simultáneas no pierden aportes. |
| CU6 Ranking y plazas | Orden descendente por puntaje, desempate por ID, bloqueo y límite de plazas; retirada sin habilitar accidentalmente a un rechazado. |
| CU7 Auditoría | Actor, fecha, operación, referencia y, para cambios de postulación, parámetros de negocio y estado anterior/posterior en JSON. Fallo de auditoría revierte el cambio. |
| CU8 Reportes | Cantidad y promedio por estado de una convocatoria, con autorización y consulta preparada. |

La nomenclatura «gestión» no se interpreta como un CRUD genérico de todas las tablas. El catálogo y las fichas del PUDS describen exactamente las operaciones disponibles; no se anuncian comandos de edición o eliminación inexistentes.

## Faltas de la revisión recibida

INSPER y LISPER se implementan sobre persona, sin sustituir a los usuarios del dominio. Se corrigió la coma final con espacios. Los dos logs locales tienen rotación. README incluye arquitectura, modelo y flujo del analizador. El PUDS contiene diagramas gráficos y fuentes editables. ZIP y TAR incluyen código, esquema, JAR, pruebas y documento. La versión se publica en la rama de la solicitud de incorporación, sin afirmar que ya está en main.

## Refuerzo de esta segunda revisión

1. El historial de calificaciones guardaba un mensaje genérico: ahora registra el criterio, valor, observación y estado anterior/posterior, dentro de la misma transacción. Los logs de archivos siguen excluyendo parámetros y códigos de confirmación.
2. Las llamadas directas al repositorio ahora también agrupan negocio y auditoría en una transacción. Una excepción de auditoría no deja una nota aplicada sin historial.
3. El bloqueo de la postulación serializa los cambios de notas y su recálculo. La prueba concurrente con exámenes 85 y 95 y méritos 80 termina en examen 90 y final 86.
4. Un bloqueo por ID de mensaje antecede a la consulta del resultado idempotente: dos reintentos concurrentes devuelven el mismo éxito y generan una sola persona y auditoría.

La compilación completa finaliza con 79 pruebas, sin fallos, errores ni omisiones, con PostgreSQL 15 aislado. Los resultados se conservan en `evidencias/`. Los ensayos de correo usan pasarela simulada o servidores locales de prueba.

## Límites que requieren comprobación institucional

- Migración y ensayo institucional completados según `ESTADO_DESPLIEGUE.md`; conservar las evidencias y practicar desde las cuentas de cada rol. El servidor PostgreSQL no admite SSL, limitación comprobada.
- Confirmar con el docente el transporte: existen IMAP/POP3/SMTP y Roundcube HTTPS, y el último es el predeterminado de la configuración heredada.
- El PDF se valida por nombre, tamaño y cabecera; no se declara análisis antivirus ni validación estructural completa.
- La ponderación 40/60 y el desempate por ID son las reglas implementadas y documentadas; no se atribuyen a un reglamento no proporcionado.
- La entrega impresa, las capturas reales y el envío de la actividad no se sustituyen por las pruebas locales.

Este informe ofrece evidencia del alcance revisado; no promete ausencia absoluta de errores ni una calificación académica.
