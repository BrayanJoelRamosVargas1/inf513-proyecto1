# Base de datos del Parcial 1

El esquema ejecutable está en [schema.sql](schema.sql). Una instalación limpia contiene **17 tablas y 18 claves foráneas**. El [modelo de datos](../docs/modelo-datos-p1-tres-capas.svg) muestra cómo las claves foráneas conectan las 17 tablas. El código Java accede a PostgreSQL mediante JDBC. Las consultas residen en `persistencia/RepositorioPostgres.java` y `persistencia/GestionCategorias.java`.

Las clases de [`modelo`](../src/main/java/bo/edu/uagrm/inf513/grupo09sa/modelo) representan los datos que usan las operaciones Java. PostgreSQL asigna identificadores y fechas de alta y conserva las restricciones y relaciones completas.

## Auxiliaturas

| Tabla | Clase en `modelo` | Uso | Relación principal |
|---|---|---|---|
| `usuario` | `Usuario.java` | Cuentas, roles y estado activo | Las postulaciones y asignaciones apuntan al usuario. |
| `convocatoria` | `Convocatoria.java` | Fechas, estado y plazas totales | Las ofertas y postulaciones apuntan a la convocatoria. |
| `cargo` | `Cargo.java` | Categorías de auxiliatura | Las ofertas y postulaciones apuntan al cargo. |
| `oferta` | `Oferta.java` | Plazas por convocatoria y cargo | Claves foráneas a convocatoria y cargo. |
| `area_examen` | `AreaExamen.java` | Áreas y ponderaciones | Clave foránea compuesta a la oferta. |
| `postulacion` | `Postulacion.java` | Solicitud, estado, puntajes y adjudicación | Claves foráneas a convocatoria, postulante y cargo. |
| `expediente` | `Expediente.java` | PDF confirmado o referencia externa | Clave foránea a la postulación. |
| `evaluacion_requisito` | `EvaluacionRequisito.java` | Historial de requisitos revisados | Clave foránea a la postulación. |
| `merito` | `Merito.java` | Historial de calificaciones de méritos | Clave foránea a la postulación. |
| `examen` | `Examen.java` | Historial de notas generales o por área | Clave foránea a la postulación. |
| `desempate` | `Desempate.java` | Decisión justificada, prioridad y versión de evaluación | Clave foránea a la postulación. |
| `asignacion_evaluador` | `AsignacionEvaluador.java` | Evaluador y tarea asignada | Claves foráneas a la postulación y al usuario evaluador. |
| `auditoria` | — | Historial de altas y cambios que consulta `AUDCAL` en el CU7 | Claves foráneas al correo, la postulación y la persona cuando corresponde. La referencia de la operación conserva el ID, código, registro, CI o nombre para AUDCAL. |

## Correo y ejemplo docente

| Tabla | Uso | Relación principal |
|---|---|---|
| `correo_control` | Recepción, cola, historial, confirmación y resultado definitivo | Conserva el hash, el vencimiento y el resultado de negocio junto al correo original. El mensaje apunta al buzón y el adjunto apunta al mensaje por claves foráneas. |
| `adjunto_correo` | PDF temporal hasta confirmar y crear el expediente | Clave foránea al mensaje original. |
| `buzon` | Estado de inicialización del buzón por protocolo | Los correos leídos por el servicio apuntan a su protocolo. Permite distinguir la primera lectura y evitar responder a los correos históricos. |
| `persona` | Catálogo requerido para `LISPER` e `INSPER`. `INSPER` usa `modelo/Persona.java`. | La auditoría de INSPER apunta a la persona registrada. Los datos de esta tabla corresponden al ejemplo del docente. |

## Comparar con un esquema anterior

El esquema renombra las tablas activas de una versión anterior mediante ALTER TABLE. Los datos y las claves foráneas se conservan. Si existen nombres de destino en un esquema compartido, la migración se detiene para evitar usar o reemplazar una tabla ajena.

En un esquema nuevo, `p1_correo_procesado` ya no se crea. Si existe en la base anterior, la migración importa los mensajes históricos a `correo_control` como `RESPONDIDO` y conserva la tabla anterior. Al comparar con TecnoWeb, esa tabla puede aparecer como legado. La migración incorpora las confirmaciones y los resultados antiguos e importa los marcadores por protocolo de `p1_servicio_estado` a `buzon`. También recupera relaciones históricas cuando puede identificar el registro correspondiente. Los correos y auditorías antiguos de origen desconocido conservan la referencia sin completar. Si esas tablas existen, quedan como respaldo. El código actualizado trabaja con la fila de `correo_control`. Ejecutá la migración con el servicio anterior detenido y verificá una copia antes de programar el cambio.

Estas consultas solo leen el catálogo. Ejecutalas en la base y el esquema que corresponde comparar.

```sql
SELECT tablename
FROM pg_tables
WHERE schemaname = current_schema()
  AND tablename IN ('adjunto_correo','area_examen','asignacion_evaluador','auditoria','buzon','cargo',
                    'convocatoria','correo_control','desempate','evaluacion_requisito','examen',
                    'expediente','merito','oferta','persona','postulacion','usuario')
ORDER BY tablename;

SELECT conrelid::regclass AS tabla, confrelid::regclass AS tabla_referenciada
FROM pg_constraint
WHERE contype = 'f' AND connamespace = current_schema()::regnamespace
ORDER BY 1, 2;
```
