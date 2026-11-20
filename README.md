# INF513 Proyecto 1 Grupo 09 SA

Sistema de gestión de convocatorias de auxiliaturas por correo electrónico con Java 17 y PostgreSQL. El [catálogo de comandos](src/main/java/bo/edu/uagrm/inf513/grupo09sa/comandos/CatalogoComandos.java) indica las operaciones de los ocho casos de usos

## Arquitectura y esquema

![Arquitectura de tres capas](docs/diagramas/arquitectura.png)

Presentación recibe y analiza el correo. Aplicación valida parámetros y roles y coordina el caso de uso. Datos aplica transacciones, mantiene la cola y ejecuta consultas preparadas en PostgreSQL. La instalación limpia crea 17 tablas y 20 claves foráneas. El esquema SQL contiene su definición y el inventario explica sus relaciones. [Esquema SQL](database/schema.sql) · [Inventario de tablas](database/README.md)

El paquete [`modelo`](src/main/java/bo/edu/uagrm/inf513/grupo09sa/modelo) reúne una clase Java por entidad de auxiliaturas, además de `Persona` para el ejemplo docente. Por ejemplo, `ASIOFE` crea una `Oferta` con los identificadores de convocatoria y cargo y el cupo. La capa de datos lee esos campos para comprobar y guardar la oferta. La definición completa de las tablas está en [`database/schema.sql`](database/schema.sql).

## Flujo del analizador

![Flujo del analizador de correos](docs/diagramas/analizador.png)

El servicio conserva el correo antes de marcarlo como leído. El analizador valida la sintaxis del asunto. El procesador comprueba los tipos y permisos, y las órdenes privadas requieren confirmación. La operación, su auditoría y el resultado se confirman en una sola transacción. La cola conserva la respuesta pendiente hasta que se pueda enviar.

## Ensayar en Fedora

Usá Java 17 o superior y Podman. Desde la raíz de esta carpeta:

```sh
bash scripts/ensayo-local.sh
```

El ensayo usa PostgreSQL local y una bandeja simulada. Escribí `AYUDA[]` en el indicador para ver las órdenes disponibles. El script muestra cómo se confirma la primera operación privada y cómo se rechaza un asunto inválido.

## Probar el código

Con un JDK 17 o superior y Maven, ejecutá `mvn clean test package`. Para habilitar la suite de PostgreSQL, configurá `TEST_DB_URL`, `TEST_DB_USER` y `TEST_DB_PASSWORD` para una base de ensayo. Si reutilizás el contenedor local, la URL es `jdbc:postgresql://127.0.0.1:55439/ensayo`, el usuario es `ensayo` y la contraseña local es `ensayo-local`.

`dist/inf513-proyecto1-1.0.0.jar` es el ejecutable completo. `dist/ensayo-local.jar` contiene el adaptador de correo simulado, cuyo código fuente está en `scripts/laboratorio`.

## Probar con el correo institucional

Copiá `config/application.example.properties` a `config/application.properties` y ajustá los hosts a los que indique la cátedra. Cargá `MAIL_PASSWORD` y `DB_PASSWORD` en el entorno privado antes de ejecutar `bash scripts/run-service.sh`. El perfil de ejemplo usa POP3, SMTP y PostgreSQL por loopback para una ejecución dentro del servidor que ofrece esos servicios. El host institucional publicado para PostgreSQL es `db.tecnoweb.org.bo` y la base asignada es `db_grupo09sa`. El código requiere cifrado para POP3 remoto. Roundcube HTTPS también está disponible para pruebas de buzón.

Las órdenes se escriben en el asunto de correos nuevos. Enviá `LISPER["*"]` para consultar `persona`. La inserción `INSPER` recibe siete parámetros en este orden: CI, nombres, apellidos, ocupación, teléfono, celular y correo. Usá datos ficticios y comprobá el rechazo al repetir el mismo CI. Para probar el analizador, enviá `LISPER[*]`. Las órdenes con rol solicitan un código que se confirma desde la misma cuenta.

Para recibir `INSPOST`, configurá primero las plazas del cargo con `ASIOFE`. `GENRAN` admite un segundo parámetro opcional para omitir filas y consultar el ranking por partes. Para resolver un empate por tandas, agregá `"INICIAR"` como quinto parámetro de `DESEMP`. Después de recibir cada respuesta, enviá la siguiente tanda con `"CONTINUAR"`, desde la misma cuenta y con la misma justificación. Las prioridades se aplican cuando se completa el grupo.

Si falla una consulta `LISPER`, el servicio prepara una respuesta de error. Con POP3, si la conexión a PostgreSQL se pierde después de inicializar la bandeja, el servicio puede avisar por SMTP a las nuevas consultas mientras la base está caída. Guarda esos avisos en `logs/consultas-sin-postgres.tsv` y los incorpora al historial al reconectar. Las escrituras esperan a que vuelva PostgreSQL. Revisá `logs/access.log` y `logs/error.log` en el equipo que ejecuta el JAR.

En una instalación existente, detené el servicio y respaldá la base. Probá `AplicarEsquema` sobre una copia antes de ejecutarlo en la base de destino. Si el instalador detecta datos históricos que necesitan una decisión del equipo, revierte los cambios e informa el motivo.


## Aplicar y comprobar el esquema

Con la configuración de la instalación en el entorno, ejecutá desde la raíz del proyecto:

```sh
java -cp dist/inf513-proyecto1-1.0.0.jar bo.edu.uagrm.inf513.grupo09sa.AplicarEsquema
java -cp dist/inf513-proyecto1-1.0.0.jar bo.edu.uagrm.inf513.grupo09sa.DiagnosticoIntegridad
```

`AplicarEsquema` ejecuta el esquema y los scripts de `database/migrations` en una transacción. `DiagnosticoIntegridad` comprueba las restricciones de la base seleccionada. Para instalar únicamente las tablas y reglas en una base nueva también podés ejecutar `database/schema.sql` con PostgreSQL.
