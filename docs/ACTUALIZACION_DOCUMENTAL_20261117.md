# Actualización documental de los ocho casos de uso

La revisión del 17 de noviembre amplía el PUDS tomando como referencia la
organización del Grupo 05SA: análisis, secuencia, clases y diseño de datos.
Conserva el dominio de auxiliaturas y los ocho casos aprobados del Grupo 09SA.
No copia las entidades ni las operaciones del sistema de barbería y no permite
anticipar una nota de evaluación.

Se incorporan ocho secuencias, ocho colaboraciones con mensajes numerados y
ocho vistas de clases. Los flujos anteriores permanecen identificados como
flujos; no se presentan como secuencias UML. Cada caso selecciona una operación
representativa, enumera sus operaciones relacionadas, reglas, alternativas,
métodos internos y suites de prueba. Los métodos y nombres de suites se
contrastan automáticamente con los archivos del código actual.

La separación en vistas responde a la implementación real: Main coordina,
ProcesadorOrden valida y autoriza, RepositorioPostgres conserva la transacción
y GestionCategorias resuelve cupos, áreas, ranking y desempates. Se muestran
las interfaces y valores Orden y Resultado; no se inventan DAO o clases de
dominio que no existen en Java. Se agrupan recepción y envío en una fachada
Main/PasarelaCorreo para mantener legibilidad, y se explica esa simplificación.
Una secuencia representa el camino confirmado; las consultas públicas de
catálogos se describen por separado y no exigen ese paso.

El diccionario cubre las veinte tablas del script: doce de negocio, auditoría
de CU7, seis auxiliares del transporte y el ejemplo persona. Incluye campos
agregados por migración, claves compuestas, índices y restricciones. La auditoría
transversal se distingue de las bitácoras locales y de las claves foráneas.

El SQL se entrega también dentro del PUDS, con una columna por línea, manteniendo
el archivo database/schema.sql. Se verificó que el cambio de formato conserva
los tokens. Se aplicaron ambas versiones a dos bases PostgreSQL 15 locales
nuevas, se reaplicó la versión legible y se compararon los esquemas completos
exportados con pg_dump: coincidieron. No se conectó a la base institucional.

El ejecutable Java no cambia en esta revisión. El resultado de 122 pruebas
corresponde a la evidencia del 15 de noviembre y la integración continua vuelve
a ejecutar la suite para comprobar el cambio. No se atribuye una nueva ejecución
local a una cifra anterior. El PUDS conserva la diferencia entre el despliegue
verificado el 14, los fallos DNS posteriores y el ensayo pendiente en Fedora.

## Fuentes y reproducción

- docs/diagramas/uml: fuentes SVG de secuencias, DOT de colaboración y clases,
  SVG y PNG renderizados y catálogo casos.json.
- scripts/documentacion/modelos_uml.py: construye modelos y valida identificadores
  de métodos y suites contra el código.
- scripts/documentacion/render_uml.cjs: renderiza fuentes con @viz-js/viz y sharp.
- scripts/documentacion/actualizar_puds.py --base COPIA_ANTERIOR.docx: amplía una
  copia del PUDS previo con python-docx y Pillow. No usar el documento ampliado
  como entrada para repetir la inserción.

El índice se actualizó con Microsoft Word y el PDF se exportó desde ese mismo
documento. El renderizador de la habilidad basado en LibreOffice se intentó,
pero este entorno no dispone de soffice.exe. Se usó el PDF nativo de Word y se
rasterizaron sus páginas con PDFium para revisar la composición. Las imágenes
de trabajo y respaldos quedan fuera del paquete académico.

## Revisión del diseño de datos

Se volvió a consultar la página 56 del PDF del Grupo 05SA y sus páginas 57 y
58 de mapeo. Las figuras 42 y 43 se sustituyeron por clases de datos con cuadros
beige, atributos individuales, tipos PostgreSQL, asociaciones nombradas y
multiplicidades. El generador lee las veinte tablas y las migraciones de
database/schema.sql: representa 134 columnas y catorce claves foráneas.

El PUDS actualizado tiene 101 páginas. La página 66 muestra las doce clases del
negocio; la 67 muestra auditoría, soporte y Persona. Ambas son láminas A3 para
evitar reducirlas al ancho carta. La página 68 hace explícito el mapeo de las
veinte clases conceptuales a las tablas físicas; sigue el diccionario completo.
Las clases de datos son nombres del modelo conceptual, no nuevas clases Java.

Las fuentes DOT y SVG editables, PNG y un PDF independiente ampliado A2 están
en docs/diagramas/datos. Los diagramas anteriores se conservan como vistas
históricas resumidas; las figuras actuales del PUDS usan los nuevos archivos.
Se mantienen los ocho casos aprobados, el esquema SQL y el ejecutable Java.

Reproducción de esta revisión, con las dependencias del runtime de documentos:

1. Ejecutar scripts/documentacion/modelo_datos.py para extraer el modelo del DDL.
2. Ejecutar scripts/documentacion/render_datos.cjs para renderizar DOT a SVG y PNG.
3. Ejecutar scripts/documentacion/actualizar_modelo_datos.py --base COPIA_PREVIA.docx
   sobre el PUDS de 100 páginas anterior a esta revisión, conservando el respaldo.
4. Actualizar el índice y exportar el PDF desde Word. Verificar las láminas
   ampliadas y la paginación del resto del documento.

La evidencia de esta revisión se conserva por separado en
evidencias/modelo-datos-20261117.json; la evidencia de 100 páginas corresponde
a la revisión documental inmediatamente anterior.
