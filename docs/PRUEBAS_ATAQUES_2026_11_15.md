# Ensayo de ataques contra el Proyecto 1

Revisión del 15 de noviembre de 2026. Alcance: servicio del Grupo 09 SA y sus ocho CU aprobados. Se usan Java 17, PostgreSQL 15 local, esquemas temporales y cuentas ficticias. No se enviaron cargas de ataque a la casilla ni a los servidores de la universidad.

## Qué se intenta romper

| Ataque | Prueba añadida | Comprobación exigida |
|---|---|---|
| SQL en el asunto completo | Diez cargas recorren AnalizadorOrden, ProcesadorOrden y PostgreSQL mediante INSPER. Incluyen comillas, OR, UNION, DROP, pg_sleep, comentarios y barras. | El nombre almacenado coincide literalmente; no desaparecen tablas ni aparecen usuarios privilegiados. |
| SQL en identificadores y notas | Las mismas diez cargas se prueban en 18 posiciones de parámetros: **180 intentos**. Incluye ofertas, áreas, postulaciones, méritos, exámenes, ranking, reportes, auditoría, plazas y desempates. | Se rechaza cada intento antes de solicitar confirmación; no se crean postulaciones, auditorías de negocio ni desafíos. |
| SQL guardado y consultado después | Una cadena con DROP se registra como nombre y unidad de cargo mediante confirmación y se busca en el catálogo; también se usa como filtro de entidad de AUDCAL. | El catálogo devuelve el texto literal, la consulta de auditoría no interpreta la entidad como nombre de tabla y el administrador sigue autorizado. |
| Segunda orden o controles en el asunto | Cinco asuntos malformados y tres variantes con CR/LF, NUL o tabulación. | Ningún intento inserta persona; una orden válida posterior sigue funcionando. |
| Lectura de solicitudes ajenas | Dos postulantes consultan sus solicitudes; uno intenta el ID ajeno, OR, porcentaje y guion bajo. También intenta LISUSU. | Cada cuenta conserva su ámbito; los filtros hostiles no muestran la otra solicitud ni habilitan operaciones administrativas. |
| Calificación de otro expediente | Un evaluador activo conoce el ID de una postulación habilitada pero no está asignado a ella. Intenta requisitos, méritos, examen general y examen por área. | No se crean evaluaciones ni se altera el puntaje ajeno; una acción sobre su postulación asignada sí funciona. |
| Reutilización concurrente de una confirmación | Dos mensajes distintos compiten por el mismo token de alta de usuario. | Solo uno ejecuta la operación: un usuario, una auditoría y rechazo de otro mensaje posterior. El reintento del mensaje ganador recupera su resultado sin duplicarlo. |
| Descarga de una URL interna | Se registra como referencia de expediente una URL dirigida a un puerto local de ensayo. | Se conserva como referencia, sin bytes PDF ni conexión al receptor local; el servicio no descarga el enlace. |

La prueba de URL interna cubre el comportamiento de la referencia externa de INSPOST, no todos los posibles mecanismos de SSRF de cualquier componente. La prueba de pg_sleep comprueba que queda almacenado como texto; no hace una deducción basada solo en tiempos de respuesta.

## Defensas que se verifican

Los valores del correo se vinculan a consultas preparadas mediante parámetros. No se intenta proteger SQL borrando comillas o palabras como SELECT: los campos de texto pueden conservarlas como datos. Los identificadores, notas, cantidad de parámetros y caracteres de control pasan por el validador antes del negocio. La consulta de postulaciones restringe por propietario y los registros de evaluación exigen asignación. Las confirmaciones se vinculan al remitente y se consumen dentro de una transacción.

Se mantiene la referencia a las guías [OWASP sobre inyección SQL](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html) y [OWASP sobre referencias directas a objetos](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html). Son guías para los controles, no una certificación del proyecto.

## Resultado y reproducción

La suite completa terminó con **119 pruebas, cero fallos, errores y omisiones**. Esta ampliación añade ocho pruebas de regresión a las 111 existentes. El resultado de la ejecución completa se conserva en `evidencias/pruebas-ataques-20261115.json` y `evidencias/maven-ataques-20261115.txt`. Las verificaciones previas de MIME, PDF, antivirus, roles, vencimiento, revocación, transacciones y límites de cola se ejecutan junto con las nuevas pruebas.

Configurar TEST_DB_URL, TEST_DB_USER y TEST_DB_PASSWORD exclusivamente para una base de ensayo donde esté permitido crear y eliminar esquemas. Después ejecutar:

```text
mvn --batch-mode --no-transfer-progress test
```

Las pruebas crean nombres de esquema aleatorios y los eliminan al finalizar. No se deben ejecutar con la base institucional. Para seleccionar las clases de ataques y seguridad:

```text
mvn -Dtest=InyeccionSqlPostgresTest,SeguridadPostgresTest test
```

## Límites de la conclusión

El resultado permite afirmar que no se reprodujeron los ataques en los recorridos ensayados. No permite prometer resistencia a cualquier ataque del docente ni ausencia absoluta de vulnerabilidades.

Se mantiene el riesgo de PostgreSQL institucional sin TLS y la necesidad de verificar el recorrido en Fedora nativo. Los comandos públicos de persona pertenecen al ejemplo académico; no deben usarse con datos personales reales. No se hicieron pruebas de denegación de servicio contra infraestructura compartida, ni pruebas de penetración del servidor Roundcube.

No hubo cambios en el código de producción como parte de esta ampliación de pruebas; el JAR de la revisión de 111 pruebas permanece igual. El PUDS conserva esa cifra como revisión funcional anterior; este informe agrega la evidencia posterior de seguridad. El ensayo real con cuentas de cada rol y el reinicio de Fedora no se consideran realizados por estas pruebas locales.
