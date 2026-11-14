# Revisión adversarial de los ocho casos aprobados

Fecha: 14 de noviembre de 2026. Se revisaron caminos de abuso del servicio de correo, además de los análisis de dependencias, secretos y código. Los ataques se ejecutaron con datos ficticios en PostgreSQL local aislado; no se hicieron ataques de carga ni intentos de acceso al servidor universitario.

## Fallos reproducidos y correcciones

La primera ejecución de las seis regresiones nuevas de persistencia produjo seis fallos. Una prueba adicional reprodujo un error MIME. Los registros anteriores a la corrección están en `evidencias/adversarial-antes.txt` y `evidencias/mime-antes.txt`. No son resultados de la versión final.

| Intento | Comportamiento anterior | Control actual |
|---|---|---|
| Repetir un mérito con el mismo criterio en otras mayúsculas | Sumaba otra vez el puntaje | Última nota por criterio normalizado y evaluador. Se promedian evaluadores por criterio y se suman los criterios, con máximo 100. Todas las filas históricas permanecen. |
| Corregir el examen del mismo evaluador | Ambas entradas participaban del promedio | Solo la última nota de cada evaluador participa del promedio. No se borra el historial. |
| Revocar un requisito después de habilitar y registrar examen | El estado anterior permitía rendir | Se comprueban de nuevo los requisitos vigentes antes de examen y plaza. Las correcciones se identifican sin distinguir mayúsculas ni espacios exteriores del requisito. |
| Asignar plaza con evaluación incompleta | Una habilitación bastaba | Se exigen registros de méritos y examen, además de requisitos aprobados. Un cero registrado se distingue de una nota ausente. |
| Cambiar notas o requisitos después de asignar una plaza | Cambiaba el resultado del ganador | Mientras exista alguna plaza asignada se bloquean notas, requisitos, asignación de evaluadores y nuevas postulaciones de esa convocatoria. Primero se retiran las plazas. La autoridad puede inhabilitar un ganador y liberar su plaza. |
| Enviar PDF desde una cuenta sin rol POSTULANTE | Se guardaba el binario antes de rechazar la orden | Se conserva la orden para responder el rechazo, pero no su archivo. Se exige usuario activo con el rol correspondiente antes de almacenar el PDF. |
| Enviar MIME multipart sin su delimitador | La excepción de análisis podía interrumpir la recepción Roundcube | Se convierte en adjunto rechazado. La prueba procesa a continuación un correo válido. Los errores de conexión conservan su mecanismo de reintento. |

## Controles adicionales comprobados

- ASIPLA respeta los primeros N candidatos elegibles por puntaje final e ID, siendo N el cupo de la convocatoria. No permite elegir al segundo lugar con una sola plaza si el primero sigue siendo elegible. GENRAN puede consultarse durante la evaluación; ese listado es provisional hasta completar las notas y requisitos.
- Las mutaciones toman primero el bloqueo de convocatoria y luego el de postulación. La decisión de plaza y la corrección de notas no pueden intercalarse entre esas verificaciones. La prueba de calificaciones concurrentes utiliza dos evaluadores distintos, ambos con el mismo peso.
- El almacenamiento temporal de PDF admite hasta 8 MiB por remitente y 64 MiB globales, con bloqueo transaccional al reservar espacio. Al saturarse no se confirma la recepción: el correo permanece disponible para reintentar. Se eliminan únicamente copias temporales de órdenes respondidas sin confirmación pendiente; los expedientes definitivos y las confirmaciones pendientes se conservan. Las confirmaciones vencidas sin consumir requieren revisión de retención por el operador: no se purgan automáticamente porque una respuesta recibida a tiempo podría estar diferida en cola.
- Las pruebas previas siguen cubriendo inyección SQL, permisos, revocación de roles, confirmaciones ajenas o repetidas, transacciones, duplicados, límites del analizador, adjuntos, transporte y recuperación.

Estas son reglas implementadas para los ocho CU, no nuevos casos de uso. Se mantiene la ponderación 40/60 y el desempate por ID. No se recibió una rúbrica académica oficial de criterios ni pesos por evaluador: los criterios siguen siendo texto libre y no se presenta esta política como reglamento de la Facultad.

## Resultado de la versión final

90 pruebas aprobadas, sin fallos, errores ni omisiones. OSV consultó 23 dependencias sin coincidencias conocidas. Se conservaron 63 alertas estáticas clasificadas; Gitleaks no detectó secretos en archivos e historial. El JAR se desplegó con respaldo y se verificaron respuestas reales, rechazo de PDF sin rol sin guardar el binario, instancia única y cola sin pendientes. Véase `evidencias/despliegue-adversarial.txt`.

## Fuentes consultadas

La revisión combinó búsquedas generales, conversaciones de foros y repositorios. Las afirmaciones técnicas se contrastaron con fuentes primarias y con pruebas del proyecto:

- [OWASP Business Logic Security](https://cheatsheetseries.owasp.org/cheatsheets/Business_Logic_Security_Cheat_Sheet.html): estados válidos, repeticiones y decisiones concurrentes; orientó las pruebas de notas y plazas.
- [Repositorio oficial OWASP WSTG](https://github.com/OWASP/wstg): guía para revisar validación, permisos y abuso del flujo.
- [OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html): autorización antes de almacenar y límites de recursos. La comprobación de cabecera PDF no sustituye un antivirus ni una revisión estructural.
- [Discusión de r/AskNetsec sobre abuso de lógica](https://www.reddit.com/r/AskNetsec/comments/1qyv0kv/are_we_lowkey_underestimating_business_logic/): se utilizó para generar ideas de ataques con órdenes válidas, no como certificación ni evidencia de que un defecto exista aquí.

## Límites de la conclusión

Una suite aprobada y los escáneres no demuestran ausencia de fallos desconocidos. Siguen pendientes el ensayo coordinado desde las cuentas de cada rol, la aprobación académica de la rúbrica y la impresión y entrega en Presencial. PostgreSQL institucional no admite TLS y requiere intervención de su administrador. El servicio no ejecuta los PDF, pero tampoco incorpora antivirus. Los límites de cola y almacenamiento reducen consumo; no garantizan disponibilidad ante un ataque sostenido o una caída de la infraestructura. Véanse `AUDITORIA_SEGURIDAD.md` y `ESTADO_DESPLIEGUE.md` para el alcance de cada evidencia.
