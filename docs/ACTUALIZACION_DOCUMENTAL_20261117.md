# PUDS con el formato del Grupo 05 SA

La revisión completa sustituye el PUDS anterior de 101 páginas. Se revisó el PDF de referencia de 69 páginas y se reconstruyó el documento con sus diez capítulos, tipos de vistas y orden. Se mantienen las reglas y los ocho casos aprobados de auxiliaturas.

Se reproducen la presentación de portada, tipografía Times New Roman, A4 vertical, numeración al pie, fichas de CU en tabla, paquetes de análisis, colaboraciones, secuencias, clases, mapeo horizontal y diccionario de siete columnas. Las figuras se agrupan por tipo, como en la referencia. El modelo de datos incluye las migraciones reales del esquema.

Los flujos de actividad, estados de cola, analizadores, catálogo extenso, operación y evidencias detalladas quedan en `ANEXO_TECNICO_PUDS.md` y en los manuales existentes. Esos contenidos siguen disponibles para el ensayo, sin aumentar los capítulos del PUDS. No cambia Java, el SQL ni el ejecutable.

La comparación `COMPARACION_PAGINA_POR_PAGINA_GRUPO05.md` identifica las 69 páginas de referencia y su correspondencia en la versión nueva. Las fuentes de las figuras vigentes se encuentran en `diagramas/puds_referencia`. Las vistas anteriores de `diagramas/uml` y `diagramas/datos` se conservan como material técnico e histórico; no son las imágenes insertadas en el PUDS vigente.

Para reproducir esta revisión se utilizan `scripts/documentacion/formato_referencia_diagramas.py`, `render_referencia.cjs`, `puds_formato_grupo05.py --base COPIA_PREVIA_101_PAGINAS.docx` y `exportar_puds_word.ps1`. Los generadores validan métodos y suites existentes y extraen las veinte tablas y 134 columnas del SQL. Word actualiza el índice y exporta el PDF; PDFium rasteriza todas las páginas para revisar la maquetación. El renderizador basado en LibreOffice no está disponible en este entorno.

Las comprobaciones institucionales y el ensayo de Fedora conservan sus fechas y limitaciones. La última evidencia de 122 pruebas y las auditorías se mantienen identificadas; el cambio de formato no demuestra disponibilidad de los servidores ni ausencia absoluta de vulnerabilidades.
