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
- Cuenta de correo del grupo con Roundcube HTTPS para lectura y respuesta

## Construcción

```text
mvn clean test package
```

El JAR ejecutable queda en `target/inf513-proyecto1-1.0.0.jar`.

## Configuración segura

Copie `config/application.example.properties` como `config/application.properties`. Las contraseñas no deben escribirse en el repositorio; configure `MAIL_PASSWORD` y `DB_PASSWORD` como variables de entorno. Si la tabla `p1_usuario` todavía está vacía, defina también `BOOTSTRAP_ADMIN_EMAIL` con el correo exacto que enviará `INSUSU` para crear el primer DECANO. El valor deja de otorgar privilegios después de esa creación.

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
Los scripts usan el JAR recién compilado de `target/` cuando existe y, dentro del paquete de entrega, recurren automáticamente al ejecutable incluido en `dist/`.

La configuración predeterminada utiliza Roundcube HTTPS tanto para leer como para responder, de modo que la contraseña no viaja por POP3 sin cifrar. POP3 continúa disponible únicamente como respaldo configurable. Si el docente habilita SMTP autenticado, puede seleccionarse con `MAIL_OUTBOUND_MODE=smtp`.

La orden completa debe escribirse en el asunto, por ejemplo `LISCON["*"]` o `AYUDA[]`.

Los comandos con datos privados o cambios de negocio requieren confirmar acceso a la cuenta de correo. Envíe la orden habitual; el sistema devuelve a esa dirección un código aleatorio y el asunto `CONFIRMAR["codigo"]` que debe enviar desde la misma cuenta. El código dura 30 minutos, autoriza exclusivamente la orden original y se consume junto con la operación en una transacción. `AYUDA`, `LISCON` y `LISCAR` permanecen públicos. Una cabecera `From` falsificada solo provoca una solicitud de confirmación al titular; no ejecuta la orden. El primer DECANO también requiere confirmación.

Para actualizar una instalación existente, detenga el proceso, aplique `database/schema.sql` mediante `AplicarEsquema` y arranque el JAR actualizado. Las migraciones conservan los datos existentes. La cola admite hasta 10.000 pendientes; la promoción respeta 60 órdenes por remitente y 1.000 globales por hora. Los excedentes esperan en la cola o en la bandeja sin marcarse como procesados. Tras una espera superior a la vigencia del código, vuelva a enviar la orden original para obtener un código nuevo.

## Seguridad

- Los comandos sensibles requieren demostrar acceso al correo y vuelven a verificar el rol al confirmar.
- Las escrituras principales utilizan consultas preparadas.
- En IMAP, los mensajes se marcan como leídos después de guardarlos de forma duradera en PostgreSQL; la cola persiste aunque falle el envío. Los excedentes sin guardar permanecen no leídos.
- Cada UIDL se ejecuta de manera idempotente: operación, auditoría y resultado comparten una transacción.
- Una respuesta que no pudo enviarse queda en una cola persistente y se reintenta sin repetir el cambio de negocio.
- PostgreSQL, POP3 y Roundcube tienen tiempos máximos y reintentos progresivos.
- Los asuntos y parámetros excesivos, los listados enormes y las ráfagas de mensajes están limitados.
- Las credenciales se mantienen fuera del código y de GitHub.
- Antes de producción se debe aplicar `database/schema.sql` y registrar las cuentas autorizadas.

Los códigos activos se almacenan como hash en `p1_confirmacion`; el correo pendiente de envío contiene necesariamente el código en su cuerpo. PostgreSQL y la cuenta institucional deben estar restringidos a operadores autorizados. No muestre códigos activos durante la defensa. El respaldo POP3 sin cifrar solo es apropiado para una red confiable porque transporta estos mensajes. La deduplicación de IMAP usa PostgreSQL como registro principal; las referencias del lote se liberan incluso si falla la lectura.

Pruebas con PostgreSQL desechable: defina `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` y ejecute `mvn clean verify`. La suite crea y elimina únicamente esquemas temporales propios, sin tocar las tablas del proyecto. GitHub Actions ejecuta estas pruebas automáticamente en PostgreSQL 15.

El plazo de confirmación se comprueba contra la hora en que el servicio registró el correo, no contra la hora posterior de ejecución. Una confirmación registrada dentro de los 30 minutos puede esperar diferida sin perder validez. Si no llegó a registrarse dentro del plazo, solicite un código nuevo.

## Recuperación ante fallos

El proceso no termina cuando PostgreSQL, POP3, Roundcube o Internet dejan de responder temporalmente. Conserva las respuestas pendientes en `p1_correo_control`, aumenta gradualmente el tiempo entre reintentos y continúa cuando el servicio vuelve. `p1_resultado_orden` impide ejecutar dos veces el mismo correo.

## Límite impuesto por la infraestructura institucional

El 12 de octubre de 2026 se comprobó que POP3 solo está disponible en el puerto 110 sin `STLS`, pero también se comprobó que Roundcube permite listar la bandeja mediante su sesión HTTPS. Por eso el modo predeterminado evita POP3 y cifra tanto la autenticación como la lectura. Si se fuerza `MAIL_INBOUND_PROTOCOL=pop3`, debe hacerse únicamente en una red confiable.
