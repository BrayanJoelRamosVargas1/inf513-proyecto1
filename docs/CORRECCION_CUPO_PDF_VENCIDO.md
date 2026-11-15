# Corrección del bloqueo por PDF con confirmación vencida

Revisión del 15 de noviembre de 2026. Alcance: disponibilidad de expedientes por correo, dentro de los ocho CU aprobados. Esta corrección continúa la revisión de 119 pruebas publicada en main.

## Fallo reproducido

Cada remitente dispone de un cupo temporal de PDF de 8 MiB. Cuatro archivos de 2 MiB con confirmaciones pendientes lo llenaban. La limpieza anterior conservaba cualquier adjunto con un código sin consumir, incluso cuando ese código ya había vencido. Por ello el remitente podía quedar bloqueado indefinidamente: no podía confirmar los archivos vencidos ni subir uno nuevo.

La prueba `pdfVencidosLiberanCupoSinBorrarExpedientesConfirmados` reprodujo el defecto antes del cambio: esperaba admitir el nuevo PDF, pero no se guardó. No se produjo una inyección SQL; se encontró un fallo de recuperación del cupo que afectaba a la disponibilidad del sistema.

## Cambio aplicado

La limpieza sigue actuando exclusivamente sobre copias temporales de correos cuya respuesta ya terminó. Conserva los PDF con códigos vigentes y aquellos cuya confirmación válida llegó dentro del plazo, aunque permanezca diferida en la cola. Libera las copias con códigos vencidos sin una confirmación válida recibida a tiempo. Los expedientes definitivos de p1_expediente y los registros de confirmación no se borran.

Para proteger una confirmación diferida se comprueban remitente, fecha de recepción, hash del token y sintaxis mediante el mismo AnalizadorOrden que ejecuta las órdenes. La selección SQL previa solo reduce candidatos y no sustituye al analizador. Se incluyen espacios Unicode admitidos por este, variación de mayúsculas y minúsculas y formato con espacios. Los identificadores protegidos se vinculan como un arreglo parametrizado; no se concatenan entradas al SQL.

La limpieza se ejecuta al admitir otro PDF, bajo el bloqueo transaccional del cupo y con un tiempo máximo en sus consultas. No incorpora un proceso independiente que borre archivos por antigüedad ni permite a otro remitente liberar o confirmar un expediente ajeno.

## Regresiones incorporadas

1. Los códigos vencidos liberan el cupo y permiten recibir otro PDF, sin crear postulaciones parciales ni borrar expedientes definitivos o el histórico de confirmaciones.
2. Una confirmación válida llegada a tiempo conserva su PDF mientras está diferida y después consigue crear el expediente con los 2 MiB originales.
3. Una confirmación de otra cuenta, un código incorrecto, una confirmación tardía, parámetros adicionales o un comando diferente que contenga el token no retienen los PDF vencidos ni permiten ejecutar la orden original.

La prueba anterior que conserva PDF con confirmaciones todavía vigentes se mantiene. La evidencia del resultado completo y del fallo previo se conserva en `evidencias/verificacion-cupo-pdf.json` y `evidencias/maven-cupo-pdf.txt`. Las pruebas usan PostgreSQL 15 local y cuentas ficticias; no se saturó el almacenamiento institucional.

## Operación y límites

La corrección no exige migrar tablas. Actualizar el JAR después de respaldar las tablas propias y detener la instancia. Conservar el ejecutable anterior para recuperación. La actualización del paquete de Fedora debe mantener el mismo JAR que la versión publicada.

Se mantienen las restricciones de 2 MiB por archivo, 8 MiB temporales por remitente y 64 MiB globales. No se limita aquí el volumen total de expedientes definitivos ni se afirma resistencia absoluta a denegación de servicio. Continúan los límites de TLS de PostgreSQL institucional y del ensayo pendiente en Fedora nativo.

## Resultado de esta revisión

La suite final terminó con **122 pruebas sin fallos, errores ni omisiones**. SpotBugs conserva 73 advertencias, con las mismas categorías y cantidades de la revisión anterior, sin errores de análisis ni clases faltantes. No se suprimieron advertencias para presentar un resultado limpio.

El JAR actualizado tiene SHA-256 `d707df9c1c36c6859456d4d8da2d00418efacf85001fd64eaa2e8252b9d194ca`. Se conservó el ejecutable anterior y se reinició la tarea local con esta versión, sin migrar tablas. No se pudo obtener un respaldo nuevo ni comprobar el recorrido institucional: el servidor de PostgreSQL no resolvió en la sonda Java y una consulta pública DNS devolvió SERVFAIL. La prueba local y la instalación del JAR no se presentan como un servicio institucional saludable. El PUDS conserva la revisión funcional anterior; este informe agrega la corrección y su evidencia posterior.
