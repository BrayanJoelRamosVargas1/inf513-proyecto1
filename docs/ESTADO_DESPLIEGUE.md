# Estado del servicio del Grupo 09 SA

Comprobado el 14 de noviembre de 2026. Alcance: ocho casos de uso aprobados para la primera parte.

## Servicio activo

El JAR auditado se ejecuta en este equipo Windows, conectado a la casilla `grupo09sa@tecnoweb.org.bo` mediante Roundcube HTTPS y a `db_grupo09sa` en PostgreSQL institucional. La comprobación encontró exactamente una instancia con el bloqueo del servicio y cero correos pendientes.

La tarea `INF513-Grupo09SA` inicia el servicio al entrar a Windows y lo reinicia si falla. Las claves se guardan cifradas con DPAPI para el usuario de este equipo, en un directorio privado fuera del repositorio y del comprimido. El iniciador está en `deploy/run-windows-private.ps1`. No se instaló Java en el servidor de la Facultad. En Fedora se utiliza el iniciador Linux y el servicio systemd incluidos, con configuración propia.

El equipo debe permanecer encendido, conectado y con la sesión del usuario iniciada; la suspensión o el apagado interrumpen el procesamiento. La tarea vuelve a iniciarse al entrar a Windows.

## Respaldo y migración

Se respaldaron exclusivamente las 15 tablas p1 existentes y sus secuencias. La primera prueba de restauración detectó sintaxis de restricciones NOT NULL de PostgreSQL 18 incompatible con el PostgreSQL 15 de ensayo. Se generó una copia compatible que conserva NOT NULL en las columnas y se restauraron correctamente las 15 tablas antes de migrar.

Los respaldos contienen datos privados y se conservaron fuera de la entrega. La migración transaccional terminó con `SCHEMA_OK`. Después se verificaron 16 tablas p1, además de `persona`, y tres barreras de integridad mediante operaciones ficticias revertidas. No se modificaron tablas de otros sistemas de la misma base.

## Pruebas reales

| Comprobación | Resultado |
|---|---|
| INSPER con datos ficticios | Respuesta exacta de éxito y una sola fila. Persona ficticia retirada al terminar. |
| Segundo correo con la misma persona | Duplicado rechazado, sin otra fila. |
| Comando inexistente | Respuesta de error recibida por la casilla del grupo. |
| PDF adjunto a INSPOST | 1488 bytes recibidos y persistidos; SHA-256 `62b8953cd0e8327037b2822f9bf17c22f9f5bb68569ed99c410d4a291ceffe2a`, idéntico al original ficticio. El remitente de ensayo no tenía rol POSTULANTE y se rechazó la orden, sin crear una postulación. |
| Respuestas automáticas propias | Se ignoran, evitando el bucle descubierto y corregido durante el ensayo. |
| Servicio y cola | Una instancia activa; cero pendientes al comprobar. |
| Suite aislada | 79 pruebas aprobadas, sin fallos, errores ni omisiones. Incluye los ocho CU y el bucle de respuestas. |

El ensayo real se dirigió exclusivamente a la casilla del propio grupo. No demuestra por sí solo el recorrido completo desde las cuentas personales de cada rol; ese recorrido está automatizado en la suite aislada y debe practicarse para defender.

## Limitación del servidor PostgreSQL

La conexión actual a PostgreSQL no usa SSL. Una conexión de prueba que exigía cifrado y verificación devolvió: «Este servidor no soporta SSL», SQLSTATE 08004. El administrador de la infraestructura debe habilitar TLS; la aplicación no puede configurar el servidor con la cuenta de base del grupo. El correo sí utiliza HTTPS.

Esta limitación se comunica expresamente: el servicio funciona, pero no se declara una infraestructura completamente segura.

## Operación en este equipo

Desde PowerShell, bajo el mismo usuario que registró la tarea:

```powershell
Get-ScheduledTask -TaskName INF513-Grupo09SA
Start-ScheduledTask -TaskName INF513-Grupo09SA
Stop-ScheduledTask -TaskName INF513-Grupo09SA
```

Los registros locales se encuentran en `logs/`. No compartir archivos de credenciales, respaldos ni registros sin revisar su contenido. Para cambiar de equipo se deben configurar de nuevo las credenciales; el archivo DPAPI no es portable.

## Entrega académica

El paquete contiene código, ejecutable, PUDS actualizado, esquema, manuales, pruebas e informes. La impresión y el envío a Presencial corresponden al equipo. No se envió la actividad desde esta sesión.
