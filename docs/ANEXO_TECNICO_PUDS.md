# Anexo técnico del PUDS

El PUDS principal sigue los diez capítulos de la referencia del Grupo 05 SA. Aquí se conservan detalles técnicos de la versión anterior para consulta y ensayo. Los resultados históricos mantienen su fecha; este anexo no afirma disponibilidad institucional actual.


## 8 Analizador y validación


## 8 1 Gramática y límites

orden       = comando "[" [ parametro { "," parametro } ] "]"
parametro   = comillas contenido comillas
comando     = letra { letra | digito | "_" }

Se admiten espacios fuera de los parámetros y se normaliza el nombre del comando a mayúsculas. Dentro de una cadena, la barra inversa permite representar una comilla o una barra. El analizador no usa la concatenación de SQL: produce una lista de valores que posteriormente se enlazan a una sentencia preparada. Después de una coma debe existir un nuevo parámetro, incluso cuando hay espacios antes del corchete final.

| Límite | Valor |
| --- | --- |
| Longitud del asunto | 998 caracteres |
| Longitud por parámetro | 500 caracteres antes de las restricciones del campo |
| Cantidad de parámetros | 20 como límite del analizador; cada comando exige su cantidad exacta |
| Resultado de listado | 200 filas y respuesta de texto acotada |
| PDF por orden | Uno de hasta 2 MiB; cabecera %PDF y nombre validado |
| Fuente MIME de carga | Hasta 4 MiB en los adaptadores de descarga de mensajes |


## 8 2 Flujo sintáctico


## 8 3 Validación semántica y autorización

ProcesadorOrden verifica primero cantidad y contenido para devolver errores útiles. El código de confirmación no concede un rol: la cuenta y el permiso se vuelven a comprobar al consumirlo. Un fallo temporal de conexión se distingue de un dato inválido para que la infraestructura pueda reintentar en lugar de finalizar una orden de manera incorrecta.


## 10 Catálogo de comandos

| Orden | Parámetros en orden | Actor |
| --- | --- | --- |
| INSPER | CI, nombres, apellidos, ocupación, teléfono, celular, correo | Público |
| LISPER | filtro | Público |
| INSUSU | registro, nombres, apellidos, rol, teléfono, correo, activo | Decano |
| LISUSU | filtro | Decano |
| INSCON | código, título, inicio, fin, estado, plazas | Decano |
| LISCON | filtro | Público |
| INSCAR | nombre, unidad, activo | Decano |
| LISCAR | filtro | Público |
| INSPOST | convocatoria ID, cargo ID, ADJUNTO o referencia | Postulante |
| LISPOST | filtro | Postulante |
| EVAREQ | postulación ID, requisito, cumple, observación | Evaluador |
| HABPOS | postulación ID, estado, observación | Decano |
| REGMER | postulación ID, criterio, puntaje, observación | Evaluador |
| REGEXA | postulación ID, puntaje, observación | Evaluador |
| ASIEVA | postulación ID, evaluador ID, tipo | Decano |
| GENRAN | convocatoria ID | Decano |
| ASIPLA | postulación ID, asignar true o false | Decano |
| AUDCAL | entidad, entidad ID | Decano |
| REPEST | convocatoria ID | Decano |
| AYUDA | sin parámetros | Público |
| CONFIRMAR | código recibido para la orden original | Remitente original |
| ASIOFE | convocatoria ID, cargo ID, plazas | Decano |
| CFGARE | convocatoria ID, cargo ID, área, peso | Decano |
| LISCFG | convocatoria ID | Decano |
| REGAREA | postulación ID, área, nota, observación | Evaluador |
| DESEMP | convocatoria ID, cargo ID, IDs ordenados, motivo | Decano |

Las operaciones marcadas Decano también admiten Vicedecano. Las privadas solicitan confirmación. CONFIRMAR es una orden especial: su código tiene un formato estricto, se liga al remitente y se consume una sola vez. Los ejemplos públicos de persona no crean cuentas ni cambian permisos.


## 10 1 Ejemplos de la actividad oficial

LISPER["*"]

INSPER["4715292","Juan Carlos","Perez Seras",
"Estudiante","33554433","71055123",
"juanperez@uagrm.edu.bo"]

El ejemplo se distribuye visualmente en varias líneas por el ancho de página. En el asunto del correo debe escribirse completo en una sola línea. La respuesta correcta del alta tiene el cuerpo Éxito: Registro insertado correctamente. El documento y las pruebas conservan esos siete valores del ejemplo docente; no corresponden a una cuenta operativa del equipo.


## 10 2 Ejemplo de postulación con archivo

INSPOST["1","1","ADJUNTO"]

Adjuntar expediente.pdf al mensaje original y sustituir ambos IDs por registros válidos del entorno. La respuesta de confirmación debe contestarse desde la misma cuenta postulante. El correo CONFIRMAR no necesita volver a adjuntar el PDF: el servicio recupera el archivo durable del mensaje inicial. La respuesta final identifica el registro y su SHA 256.


## 11 Seguridad y recuperación


## 11 1 Autorización e información privada

El campo From puede falsificarse y no se considera una demostración suficiente de identidad para las acciones sensibles del dominio. El desafío se envía a la cuenta que deberá confirmar; su hash se conserva en la base de datos. Los permisos se vuelven a validar antes de ejecutar la orden. Ni el código de un solo uso ni las contraseñas deben mostrarse en una defensa o quedar incluidos en el repositorio.

Los adaptadores mantienen tiempos máximos y verifican identidad del servidor cuando usan TLS. POP3 sin cifrado continúa como compatibilidad para la infraestructura documentada y no equivale a un canal seguro por sí mismo. El modo predeterminado usa Roundcube HTTPS; su aceptación y disponibilidad deben confirmarse con el docente en el entorno de despliegue.


## 11 2 Bitácoras y auditoría

access.log registra inicio, recepción, procesamiento y respuesta con ID, remitente, comando y resultado. error.log registra rechazos y clases de excepción; cuando corresponde agrega SQLSTATE. No se copia el mensaje crudo de excepción, que podría contener credenciales o valores SQL. Cada archivo activo rota al aproximarse a 5 MiB y se conservan tres rotaciones. El directorio predeterminado es logs y puede cambiarse con LOG_DIR o app.log.dir.

La auditoría de negocio en PostgreSQL no reemplaza estas bitácoras técnicas. La primera conserva quién cambió una entidad y el estado anterior y posterior de las calificaciones; las segundas permiten seguir el ciclo del correo y diagnosticar recepción, validación, ejecución o envío. Los logs contienen remitentes y deben conservarse con permisos del operador, sin publicarlos en GitHub.


## 11 3 Fallos y límites del servicio

| Situación | Comportamiento |
| --- | --- |
| Sintaxis o tipo inválido | Se responde un error sin SQL de negocio. |
| Clave repetida | PostgreSQL rechaza la escritura; no se agrega otra fila. |
| Base temporalmente inaccesible | El ciclo reintenta con espera progresiva, sin declarar éxito. |
| Envío no disponible | La respuesta permanece pendiente; no se vuelve a ejecutar la operación. |
| Fallo al marcar leído | La orden durable permite recuperar el trabajo y deduplicarlo. |
| Confirmación inválida | No se consume una orden válida de otra cuenta ni se concede rol. |
| Dos instancias | Un bloqueo asesor impide procesar concurrentemente la misma casilla. |
| Sin cupo de admisión | Los mensajes esperan diferidos o sin marcar; la capacidad no es ilimitada. |


## 12 Verificación y resultados


## 12 1 Entorno y resultado reproducible

La ejecución registrada el 15 de noviembre finalizó con 122 pruebas, 0 fallos, 0 errores y 0 omitidas, con Java 17 y PostgreSQL 15 aislado. El ensayo de los ocho CU atraviesa recepción, confirmaciones, ejecución y respuesta con cuatro roles ficticios. Se comprueban antivirus simulado, error del motor, tiempo excedido y cupo de archivos. Los controles y límites se describen en docs/CONTROLES_OWASP.md; el correo institucional y Defender reales se verifican por separado.

| Suite | Pruebas | Resultado |
| --- | --- | --- |
| BitacoraArchivosTest | 2 | Aprobada |
| CicloCorreoPostgresTest | 12 | Aprobada |
| AnalizadorOrdenTest | 9 | Aprobada |
| ConcurrenciaAnalizadorTest | 1 | Aprobada |
| FuzzAnalizadorTest | 1 | Aprobada |
| ProcesadorOrdenTest | 11 | Aprobada |
| AppConfigTest | 4 | Aprobada |
| ClientePop3LimiteTest | 1 | Aprobada |
| DecodificacionRoundcubeTest | 2 | Aprobada |
| LectorAdjuntosTest | 6 | Aprobada |
| PasarelaJakartaMailTest | 4 | Aprobada |
| MainTest | 5 | Aprobada |
| SeguridadPostgresTest | 47 | Aprobada |
| SeguridadTransporteTest | 6 | Aprobada |
| AntivirusPdfTest | 4 | Aprobada |
| InyeccionSqlPostgresTest | 7 | Aprobada |


## 12 2 Matriz de comprobación

| Área | Comprobación | Evidencia |
| --- | --- | --- |
| Consigna literal | INSPER exacto, LISPER, duplicado, filtro y ausencia de altas de usuarios. | PostgreSQL aislado |
| Analizador | Coma final con espacios, comillas, caracteres, límites, fuzz y concurrencia. | Pruebas automatizadas |
| Roles y confirmación | Primer DECANO, remitente incorrecto, código vencido o repetido y rol revocado. | PostgreSQL aislado |
| Ocho casos | Usuarios, convocatoria, postulación, requisitos, examen, ranking, plaza, auditoría y reporte. | Recorrido integral local |
| PDF | MIME real serializado, bytes, SHA 256, nombre, tamaño y referencia a la orden original. | MIME y PostgreSQL |
| Reversión | Sin PDF no queda una postulación parcial; un fallo de auditoría revierte la nota. | PostgreSQL aislado |
| Concurrencia | Exámenes simultáneos conservan ambos aportes; reintentos del mismo correo no duplican escrituras. | PostgreSQL aislado |
| Resiliencia | Lectura, envío y marcado fallidos con conservación de cola. | Pasarela simulada |
| Bitácoras | Archivos exigidos, metadatos, control de saltos y rotación. | Sistema de archivos temporal |


## 12 3 Caso numérico verificable

La prueba integral conserva el ejemplo de méritos 80 y examen 85, cuyo final es 83,00. La prueba por áreas usa teoría 50 al 30 % y práctica 90 al 70 %: examen 78 y final 78,80 con méritos 80. Son datos ficticios de prueba. Cupos, ranking y vacantes se calculan por cargo.


## 12 4 Comprobación institucional de esta versión

La ampliación aprobó 111 pruebas locales. Se respaldaron las 16 tablas p1_ y sus secuencias, se restauraron en PostgreSQL 15 y se probó la migración antes de aplicarla institucionalmente. Después de reiniciar el servicio en Windows se comprobaron 19 tablas p1_, una instancia y cero correos pendientes. El JAR y su SHA 256 están registrados en docs/ESTADO_DESPLIEGUE.md. PostgreSQL institucional no ofrece TLS. El ensayo en Fedora nativo sigue pendiente.

| Recorrido institucional y tareas de defensa | Evidencia que debe conservar el equipo |
| --- | --- |
| INSPER real | Inserción, respuesta y fila verificadas; persona ficticia retirada después del ensayo. |
| Error de sintaxis y duplicado | Error de comando y duplicado respondidos, sin duplicar la persona. |
| PDF recibido | Recepción real de 1488 bytes, SHA 256 idéntico y control de rol respetado. No se confirmó una postulación. |
| Casos privados por rol | Ensayar antes de defender con las cuentas de Decano, Evaluador y Postulante y fechas vigentes. |
| Transporte institucional | Roundcube HTTPS operativo. PostgreSQL responde, pero el servidor no ofrece SSL. |
| Entrega e impresión | Paquete subido en la actividad correcta, confirmación de plataforma y PUDS impreso. |


## 13 Instalación configuración y operación


## 13 1 Construcción y base de datos

1. Instalar Java 17, Maven 3.9 o superior y disponer de PostgreSQL. Para usar el JAR entregado no se requiere recompilar.

2. Clonar o extraer el proyecto. Revisar config/application.example.properties y preparar la configuración privada fuera de Git.

3. Crear una base de ensayo o preparar un respaldo lógico de la base institucional antes de una migración. Comprobar compatibilidad de persona si ya existe.

4. Detener la instancia anterior. Aplicar database/schema.sql con AplicarEsquema o con un cliente PostgreSQL autorizado.

5. Compilar y ejecutar la suite. Las pruebas de integración necesitan TEST_DB_URL, TEST_DB_USER y TEST_DB_PASSWORD dirigidos a una base de pruebas.

6. Iniciar una única instancia del servicio con su configuración y verificar logs y recepción de un correo nuevo.

mvn --batch-mode --no-transfer-progress clean verify
java -cp dist/inf513-proyecto1-1.0.0.jar bo.edu.uagrm.inf513.grupo09sa.AplicarEsquema
java -jar dist/inf513-proyecto1-1.0.0.jar


## 13 2 Configuración privada

| Variable o propiedad | Uso |
| --- | --- |
| MAIL_PASSWORD | Contraseña del buzón, únicamente en entorno privado. |
| DB_PASSWORD | Contraseña de PostgreSQL, únicamente en entorno privado. |
| BOOTSTRAP_ADMIN_EMAIL | Cuenta autorizada para el primer DECANO; deja de conceder privilegios después del alta. |
| mail.inbound.protocol | roundcube, imap o pop3 según transporte disponible. |
| mail.outbound.mode | roundcube o smtp según configuración del servidor. |
| db.url y db.username | Base y usuario autorizados para este grupo. |
| LOG_DIR | Directorio escribible de access.log y error.log. |


## 13 3 Supervisión y operación diaria

scripts/run-service.ps1 y scripts/run-service.sh supervisan el JAR; deploy/inf513-grupo09sa.service ofrece una unidad systemd para un host Linux. El operador configura las variables privadas, habilita permisos de escritura del directorio de logs y verifica que solo exista una instancia de la casilla. No se instala la unidad de ejemplo sin adaptar sus rutas y archivo de entorno.

Al estrenar un transporte, la inicialización registra el histórico para no ejecutar órdenes antiguas. Por ello, el ensayo debe enviar mensajes nuevos después de iniciar el servicio. Para detenerlo manualmente se utiliza Ctrl C; una migración exige detener la instancia y confirmar que el cambio terminó antes del reinicio. Los respaldos deben proteger tanto las tablas de negocio como las colas y archivos PDF almacenados.


## 14 Guion de defensa

| Tiempo orientativo | Demostración |
| --- | --- |
| 0 a 2 minutos | Problema, alcance aprobado y arquitectura por correo. |
| 2 a 4 minutos | Ejemplos INSPER y LISPER; mostrar que persona no crea roles de usuario. |
| 4 a 7 minutos | Convocatoria, cargos, postulación con PDF y confirmación. |
| 7 a 11 minutos | Evaluar requisito, habilitar, asignar evaluador y registrar notas. |
| 11 a 14 minutos | Resultado 83, ranking, asignación de plaza, auditoría y reporte. |
| 14 a 17 minutos | Sintaxis inválida, duplicado y bitácoras; explicar cola e idempotencia. |
| 17 a 20 minutos | Evidencias y preguntas sobre diseño, SQL, permisos y límites. |

Preparar el ensayo con datos ficticios y fechas vigentes. No repetir altas ya existentes ni asumir IDs 1 sin consultarlos. Reservar las cuentas necesarias por rol y revisar Spam si la respuesta institucional no aparece en la bandeja principal. Mantener a mano el PUDS impreso, el JAR, el código y la evidencia de la versión realmente desplegada.


## Vistas técnicas complementarias

Estas vistas permanecen como apoyo; no forman parte de los tipos de diagramas seleccionados para el PUDS de formato equivalente a la referencia.

- [Flujo técnico CU1](diagramas/flujo_cu1.png)
- [Flujo técnico CU2](diagramas/flujo_cu2.png)
- [Flujo técnico CU3](diagramas/flujo_cu3.png)
- [Flujo técnico CU4](diagramas/flujo_cu4.png)
- [Flujo técnico CU5](diagramas/flujo_cu5.png)
- [Flujo técnico CU6](diagramas/flujo_cu6.png)
- [Flujo técnico CU7](diagramas/flujo_cu7.png)
- [Flujo técnico CU8](diagramas/flujo_cu8.png)
- [analizador lexico](diagramas/analizador_lexico.png)
- [analizador](diagramas/analizador.png)
- [analizador semantico](diagramas/analizador_semantico.png)
- [estados postulacion](diagramas/estados_postulacion.png)
- [cola](diagramas/cola.png)
