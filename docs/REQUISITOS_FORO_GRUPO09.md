# Requisitos detallados publicados por el Grupo 09

Fuente consultada en la sesión autenticada el 14 de noviembre de 2026: https://presencial.uagrm.edu.bo/mod/forum/discuss.php?d=100932 . El docente aprobó los ocho casos el 5 de octubre. La publicación de requisitos es de Brayan Joel Ramos Vargas, del 13 de septiembre, y contiene un alcance más detallado que la actividad general.

La revisión inicial comprobó los ocho casos y la actividad general. Esta ampliación incorpora la lista detallada y conserva los identificadores originales en la matriz. El cumplimiento descrito se refiere a la ruta con ofertas configuradas; no al recorrido histórico sin ofertas. La validación local y el transporte institucional se documentan por separado del despliegue pendiente.

| ID original | Exigencia | Implementación de la ampliación |
|---|---|---|
| RF-01 | Administración de usuarios y roles | INSUSU, LISUSU, MODUSU y PERFIL; protección de la última autoridad activa. |
| RF-02 | Convocatorias y gestión | INSCON, LISCON y MODCON; fechas, estado, cupos, gestión y número. |
| RF-03 | Tipos de auxiliatura por convocatoria | INSCAR define tipo y unidad; OFERTA lo vincula a la convocatoria. |
| RF-04 | Plazas por categoría y unidad | OFERTA fija cupos por cargo y no excede el total; REPFIN muestra cubiertas y vacantes, incluso ofertas sin candidatos. |
| RF-05 | Alcance académico | OFERTA configura facultades y carreras; PERFIL registra el origen; INSPOST verifica coincidencia. |
| RF-06 | Requisitos configurables | REQUISITO y LISREQ por oferta; códigos, obligatoriedad, documento y estado activo. |
| RF-07 | Registro de postulación | INSPOST valida convocatoria, oferta, vigencia y perfil; confirma antes de insertar. |
| RF-08 | Postulación múltiple | Un registro por categoría; permiso múltiple controlado por las ofertas de la convocatoria. |
| RF-09 | Expediente con varios documentos | DOCPOST recibe un PDF por comando y requisito; varios comandos forman el expediente tipificado. |
| RF-10 | Documentación obligatoria | HABPOS exige bytes del PDF de cada requisito documental obligatorio; una URL no lo sustituye. |
| RF-11 | Revisión digital | LISDOC y VERDOC recuperan los documentos como adjuntos; acceso restringido al propietario, evaluador asignado o autoridad. |
| RF-12 | Cumplimiento de requisitos | EVAREQ usa códigos configurados y considera la última revisión conservando las anteriores. |
| RF-13 | Habilitación motivada | HABPOS valida requisitos, registra observación y estado; no modifica al ganador sin retirar su plaza. |
| RF-14 | Acceso al examen | NOTAREA requiere HABILITADO; comprobación de rol, área y docente. |
| RF-15 | Formulario CPD de méritos | Configurar FORM_MERITOS como requisito documental; DOCPOST almacena el formulario y VERDOC lo recupera. El equipo aporta el formulario oficial, no se inventa. |
| RF-16 | Máximo 40 de méritos | MER40 suma criterios hasta 40 y permite corregir el propio criterio con motivo. |
| RF-17 | Áreas activables | AREA y LISAREA configuran áreas por oferta antes de recibir postulaciones. |
| RF-18 | Ponderaciones por categoría | Porcentajes de áreas propios por oferta; suma exactamente 100 para admitir y evaluar. |
| RF-19 | Docentes por área y tipo | ASIAREA vincula evaluador, área y postulación; ASIEVA conserva la revisión general y méritos. |
| RF-20 | Notas por cuenta asignada | NOTAREA toma el evaluador del remitente confirmado y verifica la asignación; no acepta un docente arbitrario. |
| RF-21 | Examen hasta 60 | Promedio por docentes del área, ponderación porcentual por área y aporte de 60 puntos. |
| RF-22 | Dos decimales | numeric y redondeo del examen normalizado y total; nota 82,40 reproducida en PostgreSQL. |
| RF-23 | Suma final sobre 100 | REPFIN presenta méritos sobre 40, examen sobre 60 y suma final. |
| RF-24 | Cálculo independiente | Acumulados por postulación y áreas de su oferta; prueba con dos categorías y resultados distintos. |
| RF-25 | Ranking por categoría | GENRAN particiona por cargo y conserva igual posición para notas iguales. |
| RF-26 | Selección por cupos y posición | ASIPLA bloquea convocatoria, comprueba evaluación completa, posición y ambos cupos. |
| RF-27 | Empates en el corte | Se bloquea asignación automática; RESEMP registra autorización y motivo; no sobreautoriza el empate. |
| RF-28 | Resultados consolidados | REPFIN y REPAREA presentan notas, aportes, posición, empates y estado de selección. |
| RF-29 | Reportes del proceso | LISDOC, REPDOC, REPAREA, REPFIN y REPEST cubren documentos, estados, notas, seleccionados y vacantes. |
| RF-30 | Auditoría sensible | Actor, fecha, motivo y snapshots anteriores/posteriores con criterios y notas de áreas; transacción común. |
Esta matriz identifica diferencias comprobadas; no modifica publicaciones en Presencial ni representa un envío de la actividad.
