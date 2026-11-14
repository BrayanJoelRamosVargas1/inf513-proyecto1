# Operación por categorías y requisitos del foro

Esta es la ruta de demostración correspondiente a los 30 requisitos del foro del Grupo 09. Todos los comandos se envían en el asunto. Las escrituras y consultas privadas requieren CONFIRMAR desde la misma cuenta; AYUDA muestra el catálogo. Sustituir IDs por los obtenidos en los listados. Los ejemplos usan datos ficticios.

## Administración

| Comando | Parámetros en orden |
|---|---|
| MODUSU | usuario_id, nombres, apellidos, rol, teléfono, correo, activo, motivo |
| PERFIL | usuario_id, facultad, carrera |
| MODCON | convocatoria_id, título, inicio, fin, estado, plazas_totales, gestión, número, motivo |
| OFERTA | convocatoria_id, cargo_id, plazas_categoría, facultades, carreras, múltiple |
| LISOFE | convocatoria_id |
| REQUISITO | oferta_id, código, descripción, obligatorio, documental, activo |
| LISREQ | oferta_id |
| AREA | oferta_id, nombre, porcentaje, activo, motivo |
| LISAREA | oferta_id |

Las facultades y carreras usan `*` para toda la universidad o nombres separados por `;`, por ejemplo `SISTEMAS;REDES`. PERFIL registra los valores académicos del postulante. Cada oferta corresponde a una pareja convocatoria/cargo; el cargo representa tipo de auxiliar y su unidad, materia o laboratorio. Las ofertas no pueden sumar más plazas que el cupo de la convocatoria.

Configurar ofertas, requisitos y áreas **antes de la primera postulación**. Después se congelan esas reglas para no alterar las condiciones de quienes ya participaron. Una convocatoria configurada exige áreas activas cuya suma sea exactamente 100 antes de admitir postulantes. Desactivar áreas permite preparar distintas categorías sin eliminar su historial. Si una categoría prohíbe múltiples postulaciones, no se puede combinar con otra de la misma convocatoria.

Las convocatorias antiguas sin ofertas conservan el recorrido básico de compatibilidad. Ese recorrido utiliza REGMER/REGEXA y cupos globales y **no demuestra los requisitos detallados**. Para la entrega y defensa debe utilizarse la ruta configurada de esta guía.

## Expediente y habilitación

1. INSPOST conserva los parámetros convocatoria_id, cargo_id y `ADJUNTO` con un PDF inicial, o una referencia externa identificada como tal. Cada cargo tiene su postulación independiente.
2. Para cada documento configurado, enviar `DOCPOST["postulacion_id","CODIGO"]` con un PDF adjunto de hasta 2 MiB. Ejemplos de códigos: IDENTIDAD, HISTORICO, CARTA, CV y FORM_MERITOS. El formulario CPD se configura como documento obligatorio con el código FORM_MERITOS; no se inventa su contenido ni se sustituye por una nota.
3. DOCPOST acepta solo al propietario, durante REGISTRADA, y conserva una versión vigente por requisito. El reemplazo requiere una nueva confirmación. Las referencias externas no satisfacen los documentos obligatorios almacenados.
4. El evaluador general asignado por ASIEVA registra `EVAREQ["postulacion_id","CODIGO","true","Motivo"]`. Una revisión nueva sustituye la anterior a efectos de habilitación, conservando ambas en el historial.
5. HABPOS verifica todos los requisitos obligatorios activos y los PDFs correspondientes. INHABILITADO impide registrar examen. Un ganador debe retirar la plaza antes de cambiar la habilitación.
6. LISDOC["postulacion_id"] devuelve IDs, nombres, tamaños y huellas. VERDOC["documento_id"] devuelve el PDF como adjunto. Solo el propietario, el evaluador asignado o la autoridad pueden acceder. La consulta privada también requiere confirmación.

## Evaluación y cálculo

| Comando | Parámetros |
|---|---|
| ASIEVA | postulación, evaluador, tipo general como MERITOS |
| ASIAREA | postulación, evaluador, área_id |
| MER40 | postulación, criterio, puntos entre 0 y 40, motivo |
| NOTAREA | postulación, área_id, nota entre 0 y 100, motivo |

MER40 limita la **suma de criterios** a 40. Repetir el mismo criterio con el mismo evaluador corrige su valor en vez de sumarlo dos veces; conserva los valores anterior y nuevo y el motivo. NOTAREA permite a cada docente corregir exclusivamente su nota asignada. El examen exige HABILITADO. Si hay varios evaluadores en un área, se promedian sus notas; el área está completa cuando todos los asignados activos tienen nota. Una cuenta inactiva impide dar esa evaluación por completa.

La suma de las áreas ponderadas en escala 100 se multiplica por 0,60. Los méritos aportan hasta 40 y el examen hasta 60. El almacenamiento conserva dos decimales. Ejemplo: méritos 32, Redes 80 con 60 % y Software 90 con 40 % producen 32 + (80×0,60 + 90×0,40)×0,60 = **82,40**. Cada categoría usa sus propios porcentajes. Internamente se conservan acumulados normalizados para no reinterpretar datos anteriores; REPFIN presenta los aportes sobre 40 y 60.

## Ranking plazas y reportes

- GENRAN clasifica por categoría y mantiene igual posición para notas iguales. El ID solo ordena la presentación entre empatados y no resuelve una plaza.
- ASIPLA comprueba habilitación, evaluación completa de la categoría, cupo global y de la oferta y posición dentro de las plazas. No permite seleccionar a quien queda por debajo del corte.
- Si un empate cruza el corte, ASIPLA se bloquea. La autoridad registra `RESEMP["postulacion_id","true","Motivo del acta"]` para la persona autorizada o false para denegar. No se permite autorizar más empatados que los cupos restantes. El programa registra la decisión de la autoridad; no decide por sí solo el criterio académico del desempate.
- Las plazas asignadas congelan notas y asignaciones de evaluadores de esa categoría. Retirar las plazas permite corregir. Una nueva nota invalida las resoluciones de empate para exigir una decisión sobre los puntajes actuales.
- REPFIN["convocatoria_id"] consolida méritos, examen, total, posición, empate, seleccionados, plazas cubiertas y vacantes y un resumen de todas las ofertas, incluidas las que no tienen postulantes.
- REPDOC["convocatoria_id"] presenta documentación y revisión por postulante; REPAREA muestra docentes, notas y porcentajes; REPEST conserva los agregados por estado; AUDCAL consulta el historial privado.

Las evidencias automatizadas se encuentran en SeguridadPostgresTest y los adaptadores de correo. La conexión institucional fue autenticada, pero esto no equivale a migrar la nueva versión ni ejecutar el ciclo de negocio con el correo real.

El envío de PDF por Roundcube sigue el manejador oficial [attachment_upload.php](https://github.com/roundcube/roundcubemail/blob/master/program/actions/mail/attachment_upload.php); la respuesta durable conserva sus bytes para reintentar sin volver a consultar un documento diferente.
