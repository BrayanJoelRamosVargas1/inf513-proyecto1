# INF513 Proyecto 1 Grupo 09 SA

Sistema por correo electrónico para gestionar convocatorias de auxiliaturas. El servicio Java revisa la bandeja del grupo, interpreta la orden escrita en el asunto, valida permisos y parámetros, ejecuta la operación en PostgreSQL y responde al remitente.

## Integrantes

- 223045888 - Acosta Osorio Lin Fernanda - Ingeniería en Sistemas
- 223043982 - Ramos Vargas Brayan Joel - Ingeniería en Sistemas
- 213114976 - Rodriguez Aguilera Walter - Ingeniería en Sistemas

## Requisitos

- Java 17
- Maven 3.9 o superior
- PostgreSQL
- Cuenta de correo del grupo con POP3 para lectura y acceso al Roundcube institucional para responder

## Construcción

```text
mvn clean test package
```

El JAR ejecutable queda en `target/inf513-proyecto1-1.0.0.jar`.

## Configuración segura

Copie `config/application.example.properties` como `config/application.properties`. Las contraseñas no deben escribirse en el repositorio; configure `MAIL_PASSWORD` y `DB_PASSWORD` como variables de entorno.

## Ejecución

```text
java -jar target/inf513-proyecto1-1.0.0.jar
```

Ejecución supervisada en Windows:

```powershell
.\scripts\run-service.ps1
```

Ejecución supervisada en Fedora/Linux:

```sh
chmod +x scripts/run-service.sh
./scripts/run-service.sh
```

Para un servidor Fedora se incluye `deploy/inf513-grupo09sa.service`, que reinicia el proceso mediante systemd.

El servidor expone POP3 en el puerto 110. Para las respuestas, la configuración predeterminada utiliza el Roundcube institucional, de modo que la entrega se realiza desde el propio servidor de Tecnoweb y también funciona fuera de la red del laboratorio. Si el docente habilita SMTP autenticado, puede seleccionarse con `MAIL_OUTBOUND_MODE=smtp`.

La orden completa debe escribirse en el asunto, por ejemplo `LISCON["*"]` o `AYUDA[]`.

## Seguridad

- Los comandos sensibles verifican el rol asociado al correo remitente.
- Las escrituras principales utilizan consultas preparadas.
- Los mensajes se marcan como leídos después de enviar la respuesta.
- Cada UIDL se ejecuta de manera idempotente: operación, auditoría y resultado comparten una transacción.
- Una respuesta que no pudo enviarse queda en una cola persistente y se reintenta sin repetir el cambio de negocio.
- PostgreSQL, POP3 y Roundcube tienen tiempos máximos y reintentos progresivos.
- Los asuntos y parámetros excesivos, los listados enormes y las ráfagas de mensajes están limitados.
- Las credenciales se mantienen fuera del código y de GitHub.
- Antes de producción se debe aplicar `database/schema.sql` y registrar las cuentas autorizadas.

## Recuperación ante fallos

El proceso no termina cuando PostgreSQL, POP3, Roundcube o Internet dejan de responder temporalmente. Conserva las respuestas pendientes en `p1_correo_control`, aumenta gradualmente el tiempo entre reintentos y continúa cuando el servicio vuelve. `p1_resultado_orden` impide ejecutar dos veces el mismo correo.

## Límite impuesto por la infraestructura institucional

El 12 de octubre de 2026 se comprobó que el servidor publica POP3 únicamente en el puerto 110 y no anuncia `STLS`; POP3S 995 e IMAPS 993 no están disponibles. Por ello, la contraseña de lectura no puede protegerse con TLS desde este cliente mientras el servidor mantenga esa configuración. Ejecute el servicio solamente en la red confiable del laboratorio, en el servidor institucional o mediante una VPN administrada. El envío por Roundcube sí usa HTTPS. Esta limitación externa no debe ocultarse ni “solucionarse” desactivando validaciones TLS.
