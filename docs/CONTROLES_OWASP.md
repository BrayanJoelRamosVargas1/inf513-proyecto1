# Controles OWASP aplicados al servicio de correo

Alcance: los ocho casos aprobados del Grupo 09 SA. OWASP se utiliza como guía para seleccionar controles y pruebas; este informe no es una certificación. El proyecto procesa correo y no publica una aplicación HTTP propia. No se ejecutó ZAP contra Roundcube ni contra servidores de la universidad.

| Área revisada | Control y comprobación | Límite |
|---|---|---|
| Autorización y propiedad | Roles activos, evaluador asignado, postulación propia y confirmación ligada al remitente. El ensayo del ciclo completo usa Decano, Vicedecano, Evaluador y Postulante, con un intruso rechazado. | Esas identidades son ficticias y el transporte del ensayo es simulado; no se suplanta a personas ni se afirma acceso a sus cuentas. |
| Validación e inyección | Gramática acotada, fechas y puntajes válidos, parámetros preparados y restricciones en PostgreSQL. | Los ejemplos públicos INSPER/LISPER requieren datos ficticios por su alcance académico. |
| Integridad del flujo | Última nota por evaluador y área, requisitos vigentes, evaluación completa, ranking y cupos por cargo, bloqueo transaccional de convocatoria. | El 40/60 procede del planteamiento del grupo; los pesos por área son configurables. Un empate en el corte exige resolución motivada de la autoridad; el ID no decide una plaza. |
| Recepción de archivos | Nombre, cabecera, tamaño, rol y antivirus antes de almacenar. Directorio temporal aleatorio; el nombre del remitente no se usa como ruta. | Un resultado limpio no demuestra ausencia de malware desconocido ni validez académica del PDF. No hay CDR ni análisis estructural completo. |
| Fallo del antivirus | Un resultado distinto de cero, error o tiempo excedido no aprueba el PDF. Se responde el rechazo sin guardar bytes ni pedir confirmación; el usuario puede reenviar una orden nueva después de resolver la causa. | No se confunde la indisponibilidad del motor con una detección confirmada. |
| Disponibilidad | Cola, límites por hora, PDF de 2 MiB, cupos temporales de 8 MiB por remitente y 64 MiB globales. El exceso de PDF recibe rechazo durable y deja avanzar la bandeja. Antivirus acotado a 15 segundos por archivo y con limpieza de temporales. | Una carga sostenida o caída del servidor aún puede interrumpir el servicio. No se hicieron ataques de carga a la universidad. |
| Dependencias y secretos | OSV sobre dependencias resueltas, SpotBugs/Find Security Bugs, Gitleaks y claves privadas por entorno/DPAPI. | Las alertas estáticas se conservan y clasifican; los escáneres no garantizan cobertura total. |
| Cifrado | Roundcube HTTPS, validación de certificados y STARTTLS exigido en alternativas autenticadas sin SSL directo. | PostgreSQL institucional no admite TLS. Se conserva esa limitación visible; resolverla requiere al administrador del servidor. |
| Privilegios de ejecución | Tarea Windows limitada al usuario. Unidad Linux con usuario inf513, máscara 0077 y escritura permitida solo en logs y temporales privados. | La unidad Linux requiere preparar el usuario, permisos y antivirus en ese host; las pruebas de CI no equivalen a una instalación Fedora local. |

## Antivirus en Windows

El iniciador privado configura `PDF_ANTIVIRUS=defender`. Se localiza MpCmdRun en el directorio oficial de plataforma de Microsoft Defender. Se analiza exclusivamente el archivo temporal generado, mediante argumentos separados y sin intérprete de comandos. Se utiliza análisis personalizado con `-DisableRemediation`; cualquier salida no aprobada impide almacenar el PDF. No se envían documentos a VirusTotal ni a servicios públicos desde el programa.

Microsoft Defender debe estar activo y con firmas actualizadas. Se comprobó el motor real con un PDF ficticio benigno; las pruebas de detección, error y espera agotada utilizan un proceso de prueba, no malware real. La evidencia identifica qué parte corresponde al antivirus instalado y qué parte al simulador.

Se puede fijar `PDF_ANTIVIRUS_EXECUTABLE` a la ruta absoluta de MpCmdRun.exe. Es configuración del operador, nunca un parámetro recibido por correo. El proceso de análisis recibe solo variables técnicas mínimas: no hereda credenciales del correo, de PostgreSQL ni otras claves de la aplicación. La prueba del proceso hijo verifica que esas variables no estén presentes. El servicio no desactiva el antivirus ni modifica exclusiones del equipo.

## Antivirus y privilegios en Fedora

En el host Fedora deben instalarse ClamAV y sus firmas mediante sus paquetes oficiales. El binario esperado es `/usr/bin/clamscan`, o una ruta absoluta configurada por el operador. Se usan límites de tamaño, recursión y datos analizados; excederlos genera alerta y no se acepta como limpio.

Antes de instalar la unidad incluida:

1. Crear el usuario de sistema `inf513`, sin acceso interactivo.
2. Mantener JAR, scripts y configuración de aplicación bajo control del administrador.
3. Crear `/opt/inf513-proyecto1/logs`, propiedad de `inf513`, con permisos 0700.
4. Guardar `/etc/inf513-grupo09sa.env` con permisos 0600 y credenciales propias del host.
5. Instalar ClamAV, actualizar sus firmas y comprobar que el usuario `inf513` puede analizarlas y ejecutar el motor.
6. Instalar la unidad, iniciar una única instancia y comprobar recepción, logs y análisis de PDF.

Si el motor no está disponible, los comandos sin adjuntos siguen funcionando y los PDF se rechazan expresamente. No existe un modo de aprobar silenciosamente archivos sin analizarlos.

## Fuentes primarias

- [OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html): autorización, límites, nombres generados y análisis de archivos en capas.
- [OWASP Business Logic Security](https://cheatsheetseries.owasp.org/cheatsheets/Business_Logic_Security_Cheat_Sheet.html): secuencia de estados, repeticiones y decisiones concurrentes.
- [OWASP Denial of Service](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html): recursos acotados y aislamiento de fallos.
- [Microsoft Defender MpCmdRun](https://learn.microsoft.com/en-us/defender-endpoint/command-line-arguments-microsoft-defender-antivirus): opciones de análisis y códigos de salida.
- [ClamAV Scanning](https://docs.clamav.net/manual/Usage/Scanning.html): opciones de análisis y límites. Se activa la alerta por límites superados para no tratar archivos omitidos como aprobados.
