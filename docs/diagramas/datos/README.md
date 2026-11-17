# Modelo de datos del Grupo 09SA

Estas son las fuentes de las figuras 42 y 43 del PUDS actualizado el 17 de
noviembre de 2026. Siguen la presentación de clases de datos de la página 56
del PDF del Grupo 05SA, con las entidades y las relaciones de auxiliaturas.

- `clases_datos`: doce clases del negocio con sus atributos, tipos y trece FK.
- `clases_soporte`: auditoría de CU7, seis tablas del correo y Persona. Solo
  ControlCorreo y AdjuntoCorreo están relacionados mediante una FK física.
- `mapeo.json`: veinte tablas y 134 columnas del DDL incluidas sus migraciones.
- `MODELO_DATOS_AMPLIADO.pdf`: dos láminas A2 para lectura e impresión ampliada.

Los archivos SVG permiten ampliar y editar sin pérdida de definición. El PDF
independiente contiene imágenes de alta resolución. El PUDS incorpora las dos
vistas en A3 vertical; al imprimir en carta se reducirán los textos.

La notación `1` significa un registro padre obligatorio para cada registro
hijo, `0..*` permite cero o muchos dependientes y `0..1` permite uno opcional.
Las líneas continuas representan FK reales. La correlación entre tablas por
mensaje, actor o entidad se explica en el texto y no se representa como FK.

Los nombres de las cajas son clases conceptuales de datos. La tabla de mapeo
del PUDS indica sus nombres físicos; no declara nuevas clases Java ni tablas.

