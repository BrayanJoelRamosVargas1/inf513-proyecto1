# Seguridad y resiliencia

## Amenazas contempladas

- Caída temporal de Internet, POP3, Roundcube o PostgreSQL.
- Reinicio inesperado del proceso o del sistema operativo.
- Reentrega del mismo correo y ejecución concurrente de dos instancias.
- Asuntos mal formados, parámetros excesivos y ráfagas de mensajes.
- Consultas lentas, resultados demasiado grandes e intentos de inyección SQL.
- Exposición accidental de contraseñas en el repositorio o en los mensajes de error.

## Controles implementados

- Cola persistente con estados `RECIBIDO`, `RESPUESTA_PENDIENTE` y `RESPONDIDO`.
- Idempotencia por UIDL: negocio, auditoría y resultado se confirman en una transacción.
- Reintento exponencial con límite y variación aleatoria.
- Reconexión automática; HikariCP no exige que PostgreSQL esté disponible al arrancar.
- Tiempos máximos para conexión, lectura HTTP, POP3 y consultas SQL.
- Aislamiento por mensaje: un correo defectuoso no bloquea los siguientes.
- Límites de tamaño, número de parámetros, mensajes por ciclo, filas y respuesta POP3.
- Consultas preparadas, control de roles y secretos exclusivamente por variables de entorno.
- Validación de tipos, rangos, fechas, estados, longitudes y caracteres de control antes de acceder a PostgreSQL.
- Restricciones `CHECK` en PostgreSQL como segunda barrera para fechas invertidas y puntajes fuera de 0 a 100.
- Bloqueo asesor de PostgreSQL: solo una instancia del servicio procesa la bandeja al mismo tiempo.
- Respuestas SQL sanitizadas y resultados limitados a 200 filas y 50.000 caracteres.
- Respuestas HTTP limitadas a 5 MB y navegación de Roundcube acotada para evitar agotamiento de memoria.
- La configuración rechaza puertos, intervalos, cantidades peligrosas, protocolos desconocidos y secretos sin resolver.
- Apagado seguro y supervisores para Windows, Linux y systemd/Fedora.
- Unidad systemd con privilegios reducidos, sistema protegido y directorio temporal privado.

## Garantía realista

El sistema se recupera de fallos temporales y evita repetir cambios de negocio. La entrega de correo es de tipo *al menos una vez*: si Roundcube envía una respuesta y la conexión se corta antes de confirmarlo, puede producirse una respuesta duplicada, pero no una operación duplicada en PostgreSQL. Ningún software puede continuar comunicándose mientras todos los servidores o la red estén apagados; la garantía es reanudar el trabajo pendiente cuando vuelvan.

## Transporte seguro

Aunque el servidor institucional solo expone POP3 110 sin `STLS`, el sistema lee y responde de forma predeterminada mediante la sesión HTTPS de Roundcube. POP3 queda como respaldo manual para una red confiable. No se guardan contraseñas en GitHub, documentos ni paquetes.

## Pruebas recomendadas para la defensa

1. Iniciar el servicio sin PostgreSQL disponible y restaurarlo: debe conectarse sin reiniciar manualmente.
2. Cortar Internet después de registrar una orden y restaurarlo: la respuesta pendiente debe enviarse sin repetir el cambio.
3. Reiniciar Java con una respuesta pendiente: debe continuar desde PostgreSQL.
4. Reenviar el mismo mensaje/UIDL: no debe duplicar méritos, examen ni postulación.
5. Enviar un comando inexistente, parámetros incorrectos y un remitente sin rol: debe responder `ERROR`.
6. Enviar un asunto mayor al límite: debe rechazarse sin detener el servicio.
7. Ejecutar en Windows con `scripts/run-service.ps1` y en Fedora con `scripts/run-service.sh` o systemd.
