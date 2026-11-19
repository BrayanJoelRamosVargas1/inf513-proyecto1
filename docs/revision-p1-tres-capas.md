# Parcial 1 del Grupo 09 SA

La versión local reorganiza el programa Java en tres capas, consolida el historial de correo y agrega controles de integridad en PostgreSQL. Está preparada para ensayar en Fedora con el esquema real y una bandeja simulada.

## Qué se tomó de la referencia

El proyecto BarberSeven del Grupo 05 es un Parcial 2 web. Su informe, ilustración 14 en la página 43, distingue presentación en Vue/Inertia, aplicación en Laravel y datos en PostgreSQL. El código lo concreta con páginas, controladores y solicitudes HTTP, servicios y modelos con migraciones. La ilustración 32, página 66, muestra el modelo conceptual. Incluye una Bitácora conectada con Usuarios. El código también tiene migraciones para sesiones, caché y tareas.

Las migraciones PHP del paquete declaran doce tablas propias de BarberSeven y siete tablas de Laravel. La presentación de su diagrama conceptual omite algunas tablas físicas, como `visitas_pagina` y las de Laravel. Es una referencia para ordenar el proyecto y explicar cada tabla. El contenido pertenece a su propio negocio de citas y ventas.

## Cómo queda nuestro código

| Capa | Componentes | Responsabilidad |
|---|---|---|
| Presentación por correo | `presentacion/ControladorCorreo.java`, `email/`, `AnalizadorOrden` | Recibir mensajes, interpretar el asunto, aplicar controles de entrada y entregar respuestas. |
| Aplicación | `comandos/ProcesadorOrden.java`, `CatalogoComandos`, `RepositorioProyecto` | Coordinar los casos de uso, validar parámetros y roles, solicitar confirmación y ejecutar por contrato. |
| Datos | `persistencia/RepositorioPostgres.java`, `GestionCategorias.java`, `database/schema.sql` | Ejecutar SQL preparado, mantener transacciones y aplicar restricciones relacionales. |

`Main.java` ensambla las capas e inicia el ciclo. La presentación usa la interfaz `ColaCorreo`. La aplicación declara su propio contrato `RepositorioProyecto`. Ambos contratos se implementan en PostgreSQL. Parte de las reglas de cálculo y cupo ya reside en las consultas de datos y se conserva allí para mantener el comportamiento previo.

## Los ocho casos de uso

| Caso | Qué cubre | Comandos |
|---|---|---|
| CU1 | Usuarios y roles | `INSUSU`, `LISUSU` |
| CU2 | Convocatorias, cargos y cupos | `INSCON`, `LISCON`, `INSCAR`, `LISCAR`, `ASIOFE`, `LISCFG` |
| CU3 | Postulaciones y expediente PDF | `INSPOST`, `LISPOST` |
| CU4 | Requisitos, habilitación y acceso al examen | `EVAREQ`, `HABPOS` |
| CU5 | Evaluadores, méritos y examen | `ASIEVA`, `REGMER`, `REGEXA`, `CFGARE`, `REGAREA` |
| CU6 | Ranking, desempates y asignación de plazas | `GENRAN`, `DESEMP`, `ASIPLA` |
| CU7 | Seguimiento y auditoría de calificaciones | `AUDCAL` y registro de las operaciones |
| CU8 | Reportes y estadísticas | `REPEST` |

`AYUDA` muestra los comandos, `CONFIRMAR` autoriza una orden pendiente y `LISPER` e `INSPER` reproducen el ejemplo de la consigna. Estos son comandos auxiliares a los ocho casos del proyecto. En el CU8, `REPEST` muestra el cupo global cuando la convocatoria no tiene ofertas por cargo. Cuando hay ofertas configuradas, el consolidado y el resumen usan las plazas de cada oferta.

En el CU5, el tercer parámetro de `ASIEVA` delimita la calificación autorizada. `MERITOS` habilita `REGMER`. `EXAMEN` habilita `REGEXA` para examen general o `REGAREA` si hay áreas configuradas. `MERITOS_Y_EXAMEN` cubre ambos. Para asignar por nombre, primero configurá el área para el cargo de la postulación. `ASIEVA` acepta los mismos 80 caracteres que `CFGARE` y rechaza nombres que no correspondan a un área configurada. `EVAREQ` exige un evaluador asignado a la postulación. Antes de actualizar TecnoWeb, hay que comprobar los valores históricos de `p1_asignacion_evaluador.tipo`.

### Revisión por caso y prueba manual

Usá las cuentas de ensayo y los identificadores que devuelva la aplicación. Cuando una orden privada responda con un código de confirmación, enviá el asunto `CONFIRMAR` indicado en la respuesta desde esa misma cuenta. Entre el envío de la orden y la confirmación, verificá que todavía no se haya aplicado el cambio.

| Caso | Qué comprobamos en local | Cómo comprobarlo manualmente |
|---|---|---|
| CU1 | Alta de los cuatro roles, alta inicial desde la cuenta autorizada, listado privado, rechazo de códigos vencidos y de altas duplicadas. El primer decano debe quedar activo. PostgreSQL compara el correo sin distinguir mayúsculas. | En una base de ensayo vacía enviá el alta inicial con `false`. Debe devolver que el primer decano tiene que quedar activo. Reenviá la orden con `true`, confirmala y buscá el registro con `LISUSU`. |
| CU2 | Convocatoria con fechas y estados validados, catálogo público de convocatorias y cargos, cupos por cargo y rechazo cuando dos ofertas concurrentes exceden el total. | Creá una convocatoria con una plaza y dos cargos. Ofertá una plaza en el primero con `ASIOFE` e intentá ofertar otra en el segundo. Consultá `LISCFG` para ver el cupo aceptado. |
| CU3 | Carga de un PDF desde el correo original, conservación de sus bytes y huella, rechazo por adjunto ausente, protección contra reintentos y listado del propio postulante. La modalidad de referencia externa guarda la dirección. | Enviá una postulación con `ADJUNTO` y un PDF ficticio. Confirmá y verificá que la respuesta incluya ID y SHA-256. Repetí la misma combinación de convocatoria, postulante y cargo y buscá el resultado con `LISPOST`. |
| CU4 | Una evaluación negativa impide habilitar. Una corrección conserva el historial. Una postulación no habilitada o con un requisito revocado queda fuera del examen. | Con `EVAREQ`, marcá un requisito como falso e intentá `HABPOS`. Corregilo a verdadero, confirmá la habilitación y luego intentá registrar la nota desde una cuenta evaluadora asignada. |
| CU5 | Permisos por tipo o área, áreas que suman 100 %, corrección de nota sin duplicar el aporte del evaluador, congelación de ponderaciones después de registrar exámenes y fórmula 40/60. Se verificó una asignación a un nombre de más de 40 caracteres. | Asigná méritos a un evaluador y comprobá que pueda usar `REGMER`. Para el examen, configurá un área con 100 %, asignala y usá `REGAREA`. Con méritos 80 y examen 85, el final debe ser 83,00. |
| CU6 | Ranking por cargo, orden y cupos. Si se suma otro candidato al empate o se corrige una nota y quedan prioridades incompletas, el ranking y el reporte muestran a todo el grupo como pendiente. La asignación de una plaza en el corte exige una decisión válida. | Prepará dos postulaciones completas con el mismo puntaje para una plaza y resolvé el empate con `DESEMP`. Agregá una tercera con el mismo puntaje. `GENRAN` debe mostrar a las tres con la misma posición. Reintentá `ASIPLA` después de resolver el empate para las tres. |
| CU7 | Auditoría con actor y estado anterior y posterior al modificar una nota. Si falla la escritura de auditoría, se revierte la calificación. Los IDs de base de datos se pueden consultar aunque el correo original incluya ceros a la izquierda. El acceso se comprueba por rol. | Cambiá un mérito usando el ID de prueba escrito como `0001` y consultá `AUDCAL["REGMER","1"]` desde administración. Para una convocatoria consultá `AUDCAL["INSCON","CÓDIGO"]` y para un cargo `AUDCAL["INSCAR","NOMBRE"]`. Repetí desde una cuenta de postulante para comprobar el rechazo. |
| CU8 | Reporte por estado, consolidado, áreas, ranking y vacantes. Se probaron ofertas por cargo, cupo global y 231 postulaciones. Un reporte extenso conserva las cinco secciones y avisa en cada sección donde se omitieron filas. | Ejecutá `REPEST` antes y después de adjudicar la única plaza. El resumen debe pasar de una vacante a una plaza cubierta y cero vacantes. |

El recorrido automatizado de los ocho casos usa PostgreSQL y un buzón de ensayo. La prueba de protocolos ejecuta el JAR contra servicios POP3 y SMTP locales. La entrega institucional de correo, los permisos reales de las cuentas y la migración sobre los datos de TecnoWeb siguen pendientes.

En `AUDCAL`, la referencia de `INSPOST` es el ID de convocatoria, la de `INSUSU` es el registro y la de `INSPER` es el CI. Los ceros iniciales de registros y CI forman parte del identificador. La columna `p1_auditoria.entidad_id` admite hasta 160 caracteres para registrar el nombre completo de un cargo.

## Inventario de la base nueva

| Grupo | Tablas | Decisión |
|---|---:|---|
| Auxiliaturas y auditoría | 13 | Usuarios, convocatorias, cargos, ofertas, áreas, postulaciones, expedientes, requisitos, méritos, exámenes, desempates, asignaciones y auditoría. `AUDCAL` sigue consultando `p1_auditoria`. |
| Recepción de correo | 5 | Control e historial, adjuntos temporales, confirmación, resultado idempotente y estado del buzón. |
| Ejemplo de la consigna | 1 | `persona` sigue sirviendo a `LISPER` e `INSPER` con datos ficticios. |
| **Total** | **19** | **14 claves foráneas** declaradas y vínculos lógicos indicados con líneas discontinuas. |

La instalación anterior guardaba cada respuesta exitosa o fallida en dos tablas. Esta versión usa `p1_correo_control` también para el historial y reconoce los mensajes ya procesados por el estado `RESPONDIDO`. En una base nueva, `p1_correo_procesado` ya no se crea. En una base anterior, el esquema importa los históricos que faltan sin cambiar las filas de la cola. Conserva la tabla antigua como respaldo para compatibilidad. El resultado de negocio se mantiene separado porque se persiste de forma transaccional para evitar repetir operaciones si falla el envío.

PostgreSQL rechaza por SQL directo las ofertas que superan en conjunto las plazas de una convocatoria y las reducciones del total por debajo de lo ofertado. Si una transacción intenta modificar las ofertas usando una versión anterior de la convocatoria, debe reintentar con datos vigentes. La base también valida el cargo de cada postulación cuando hay ofertas, el límite de 100 % en las ponderaciones y el área y evaluador de las notas. Cuando una categoría ya tiene notas de examen, PostgreSQL también protege la configuración de áreas contra altas, modificaciones y bajas. Esto cubre el examen general y el ponderado. El modo histórico de cupo global se conserva para convocatorias sin ofertas. Los controles se aplican a nuevas escrituras y a cambios de las columnas relacionadas. Si hay filas históricas incompatibles, las conserva para revisión.

Los códigos de confirmación pendientes conservan su valor durante los reintentos de SMTP. Al registrar el envío aceptado se fija un plazo de 30 minutos para el código sin usar y se oculta el código del cuerpo guardado en el historial.

## Cómo abrir el ensayo

En Fedora, extraé el ZIP, entrá en `inf513-proyecto1` y ejecutá:

```sh
bash scripts/ensayo-local.sh
```

Necesitás Java 17 o superior y Podman. El script reutiliza o crea un contenedor PostgreSQL 15 de ensayo, expuesto solo en `127.0.0.1:55439`. La primera ejecución puede necesitar descargar su imagen. El laboratorio inicializa un esquema propio y muestra una consulta pública, un rechazo de sintaxis y el flujo de confirmación. Después podés escribir `AYUDA[]` o `/salir`.

## Estado de TecnoWeb

Esta revisión es local. Quedan pendientes la aplicación del esquema actualizado en TecnoWeb, la comprobación del servicio remoto y la prueba de correo institucional por POP3 y SMTP. Al recuperar el acceso, respaldá la base, comprobá las tablas y los datos históricos y ensayá la migración sobre una copia antes de reemplazar SQL y JAR en el servidor.

## Verificación

Una compilación limpia con Maven y JDK 17 ejecutó **137 pruebas con éxito** en PostgreSQL 15. En una base vacía se verificó el rechazo del primer decano inactivo, el alta posterior del decano activo y la posibilidad de registrar un evaluador inactivo una vez iniciado el sistema. El recorrido por correo cubrió los ocho casos. Una prueba con 231 postulaciones comprobó las cinco secciones del reporte. El ejecutable procesó una consulta válida y un asunto inválido por POP3 y SMTP contra servicios locales de ensayo y se recuperó de un conflicto con el bloqueo de instancia única. El SQL se aplicó dos veces sobre una copia del esquema anterior y el laboratorio arrancó desde una extracción limpia.


## Depuración del paquete local

Se retiraron cuatro utilidades independientes del JAR: `AuditoriaAdversarialDb` y `SmokeProyecto`, cubiertas por la suite y el ensayo local, y `InicializarBandeja` e `ImportarBaseline`, que usaban el marcador antiguo del buzón. El servicio inicializa el historial por protocolo y el SQL importa los registros anteriores. La revisión retiró además el método sin llamadas que cargaba todo el historial de correo y la opción de admisión que el repositorio nunca devolvía.

`AplicarEsquema` aplica el archivo entero en una transacción JDBC para conservar los bloques de PostgreSQL. Se probó desde el JAR con un esquema vacío, sobre el esquema anterior y con un error de SQL que forzó la reversión. Los diagnósticos de estructura consultan el esquema seleccionado por la conexión.

La bitácora vuelve a crearse si se mueve su archivo activo. Un fallo al marcar un correo como leído después de enviarlo se anota en la bitácora y deja la respuesta en estado `RESPONDIDO`. Al confirmar que salió un correo de autorización, la cola oculta el código en la copia de la respuesta. Si SMTP falla, se conserva el código pendiente de envío.
