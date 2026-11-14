# Revisión para la defensa

## Correcciones funcionales

- Se conserva el dominio de los ocho casos aprobados y se añaden los ejemplos literales `LISPER` e `INSPER` en una tabla `persona` independiente.
- El analizador rechaza también la coma final seguida de espacios, tabulaciones o saltos antes del corchete.
- Cada ciclo registra recepción, procesamiento, resultado, envío y fallos en `logs/access.log` y `logs/error.log`. Los archivos rotan y no contienen parámetros, códigos de confirmación ni mensajes de excepción con credenciales.
- `INSPOST["convocatoria_id","cargo_id","ADJUNTO"]` permite cargar un PDF adjunto de hasta 2 MiB. La recepción guarda asunto y PDF en la misma transacción antes de marcar el mensaje leído. Tras confirmar, se almacenan los bytes, el nombre y SHA-256 en `p1_expediente`. Una referencia de ruta o URL sigue siendo una referencia externa y se identifica como tal en la respuesta.
- La confirmación y la idempotencia se mantienen: recibir un PDF no ejecuta todavía una postulación ni concede acceso al examen.

## Orden recomendado de demostración

1. Mostrar `AYUDA[]`, `INSPER` con los siete valores de la consigna y `LISPER["*"]`.
2. Repetir el alta para mostrar el rechazo del duplicado sin otra fila.
3. Mostrar la creación de convocatoria y cargo con el DECANO, usando IDs y fechas reales del entorno de ensayo.
4. Enviar `INSPOST` con `ADJUNTO` y un PDF ficticio, confirmar desde el correo del POSTULANTE y verificar nombre, tamaño y SHA-256 en PostgreSQL.
5. Registrar requisitos, habilitar, asignar evaluador y registrar méritos 80 y examen 85. El resultado final esperado es 83,00.
6. Consultar ranking, asignar plaza, consultar auditoría y estadísticas.
7. Mostrar un asunto inválido, un permiso insuficiente y la conservación de una respuesta cuando se simula una caída del envío en el entorno de prueba.
8. Abrir `access.log` y `error.log`, sin mostrar contraseñas ni códigos activos.

## Estado institucional actual

La migración se aplicó después de restaurar el respaldo. El servicio está activo en Windows con inicio automático al entrar a la sesión. Se comprobaron inserción, duplicado, comando inválido y recepción real de PDF; véase `ESTADO_DESPLIEGUE.md`. El servidor PostgreSQL no admite SSL. Practiquen el recorrido de defensa desde las cuentas de cada rol y confirmen el transporte aceptado.

El PDF del PUDS debe imprimirse para la defensa. El Word queda editable para completar evidencias institucionales y ajustes de portada. No se inventaron capturas de Gmail, notas otorgadas por el docente ni una publicación final en la plataforma.
