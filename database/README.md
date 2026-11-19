# Base de datos del Parcial 1

El esquema ejecutable está en [schema.sql](schema.sql). Una instalación limpia contiene **19 tablas y 14 claves foráneas**. El [modelo de datos](../docs/modelo-datos-p1-tres-capas.svg) distingue las claves foráneas de los vínculos que mantiene la aplicación. El código Java accede a PostgreSQL mediante JDBC. Las consultas residen en `persistencia/RepositorioPostgres.java` y `persistencia/GestionCategorias.java`.

## Auxiliaturas

| Tabla | Uso | Relación principal |
|---|---|---|
| `p1_usuario` | Cuentas, roles y estado activo | Las postulaciones y asignaciones apuntan al usuario. |
| `p1_convocatoria` | Fechas, estado y plazas totales | Las ofertas y postulaciones apuntan a la convocatoria. |
| `p1_cargo` | Categorías de auxiliatura | Las ofertas y postulaciones apuntan al cargo. |
| `p1_oferta` | Plazas por convocatoria y cargo | Claves foráneas a convocatoria y cargo. |
| `p1_area_examen` | Áreas y ponderaciones | Clave foránea compuesta a la oferta. |
| `p1_postulacion` | Solicitud, estado, puntajes y adjudicación | Claves foráneas a convocatoria, postulante y cargo. |
| `p1_expediente` | PDF confirmado o referencia externa | Clave foránea a la postulación. |
| `p1_evaluacion_requisito` | Historial de requisitos revisados | Clave foránea a la postulación. |
| `p1_merito` | Historial de calificaciones de méritos | Clave foránea a la postulación. |
| `p1_examen` | Historial de notas generales o por área | Clave foránea a la postulación. |
| `p1_desempate` | Decisión justificada, prioridad y versión de evaluación | Clave foránea a la postulación. |
| `p1_asignacion_evaluador` | Evaluador y tarea asignada | Claves foráneas a la postulación y al usuario evaluador. |
| `p1_auditoria` | Historial de altas y cambios que consulta `AUDCAL` en el CU7 | Referencia lógica por operación y su identificador. Según la operación se guarda un ID, un código, un registro, un CI o un nombre. |

## Correo y ejemplo docente

| Tabla | Uso | Relación principal |
|---|---|---|
| `p1_correo_control` | Recepción, cola, reintentos de envío e historial | El adjunto apunta a su mensaje por clave foránea. |
| `p1_adjunto_correo` | PDF temporal hasta confirmar y crear el expediente | Clave foránea al mensaje original. |
| `p1_confirmacion` | Orden pendiente, hash del código, vencimiento y consumo | Vínculo lógico al mensaje original y al mensaje de confirmación. |
| `p1_resultado_orden` | Resultado de negocio persistido para que un reintento no duplique una operación | Clave de idempotencia derivada del mensaje u orden confirmada. |
| `p1_servicio_estado` | Marca que indica si ya se inicializó el buzón para el protocolo usado | Estado general del servicio. Permite distinguir la primera lectura y evitar responder a los correos históricos. |
| `persona` | Catálogo requerido para `LISPER` e `INSPER` | Catálogo independiente de las cuentas de auxiliaturas. |

## Comparar con un esquema anterior

En un esquema nuevo, `p1_correo_procesado` ya no se crea. Si existe en la base anterior, la migración importa los mensajes históricos a `p1_correo_control` como `RESPONDIDO` y conserva la tabla anterior. Al comparar con TecnoWeb, esa tabla puede aparecer como legado. La presencia de otras diferencias se verifica consultando el esquema conectado.

Estas consultas solo leen el catálogo. Ejecutalas en la base y el esquema que corresponde comparar.

```sql
SELECT tablename
FROM pg_tables
WHERE schemaname = current_schema()
  AND (tablename = 'persona' OR tablename LIKE 'p1\_%' ESCAPE '\')
ORDER BY tablename;

SELECT conrelid::regclass AS tabla, confrelid::regclass AS tabla_referenciada
FROM pg_constraint
WHERE contype = 'f' AND connamespace = current_schema()::regnamespace
ORDER BY 1, 2;
```
