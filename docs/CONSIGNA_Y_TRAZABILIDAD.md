# Consigna oficial y alcance aprobado

Fuente leída en la sesión autenticada del estudiante el 14 de noviembre de 2026: [INF513-SA Entrega Proyecto No.1 — Sistemas vía Mail](https://presencial.uagrm.edu.bo/mod/assign/view.php?id=1510296). La actividad muestra apertura el 20 de noviembre de 2026 a las 00:00 y cierre ese día a las 15:00. No se realizó ningún envío a la plataforma.

El docente Evans Balcazar Veizaga aprobó los ocho casos de uso del Grupo 09 el 5 de octubre de 2026, según la publicación facilitada por el estudiante. Se conserva ese dominio: usuarios, convocatorias, postulaciones y expediente, requisitos y habilitación, méritos y examen, ranking y plazas, auditoría y estadísticas.

La sección 3 de la actividad contiene además estos ejemplos literales, que esta revisión incorpora sin sustituir los casos aprobados:

```text
LISPER["*"]
INSPER["4715292","Juan Carlos","Perez Seras","Estudiante","33554433","71055123","juanperez@uagrm.edu.bo"]
```

La tabla `persona` es independiente de `p1_usuario`. El orden de campos adoptado para el ejemplo es CI, nombres, apellidos, ocupación, teléfono, celular y correo. La actividad proporciona el orden y los valores; los nombres de columnas son la correspondencia técnica usada en esta implementación. Las personas del ejemplo no reciben roles de auxiliaturas. Ambos comandos son públicos para reproducir el recorrido directo de la consigna; no se deben cargar allí datos reales de postulantes durante una demostración pública.

| Exigencia observada | Implementación o evidencia |
|---|---|
| Correo como interfaz, orden en el asunto y respuesta al remitente | `Main`, `PasarelaCorreo` y adaptadores; pruebas del ciclo con buzón simulado |
| `LISPER` e `INSPER` sobre `persona` | Catálogo, validación, SQL preparado y pruebas con el ejemplo literal, duplicado y filtro |
| Espacios, UTF-8 y comillas escapadas | Analizador y pruebas; regresión de la coma final con espacios |
| Transacciones y validación de tipos | `RepositorioPostgres`, restricciones SQL y pruebas con PostgreSQL aislado |
| `access.log` y `error.log` locales | `BitacoraArchivos`; 5 MiB por archivo activo y tres archivos rotados |
| README con arquitectura, datos, instalación y flujo del analizador | `README.md` y diagramas editables en `docs/diagramas` |
| PUDS con carátula, arquitectura, MER y casos de uso | Word editable y PDF en `entrega/` |
| ZIP o TAR.GZ con nomenclatura exacta | Paquete `2026-2_INF513-P1_grupo09sa` |
| GitHub personal público | Repositorio público verificado; cambios de esta revisión en rama y solicitud de incorporación |
| Ejecución actualizada en infraestructura institucional | Pendiente de migración y prueba con credenciales reales; la suite local no equivale a este despliegue |

La actividad menciona IMAP/POP3/SMTP según configuración de cátedra. El proyecto conserva esos adaptadores y usa Roundcube HTTPS por defecto debido a las restricciones documentadas previamente en el servidor. Debe demostrarse el transporte aceptado por el docente en el despliegue institucional; esta revisión no lo presenta como vuelto a probar.

## Requisitos detallados del foro

Se consultó además el [hilo propio del Grupo 09](https://presencial.uagrm.edu.bo/mod/forum/discuss.php?d=100932), que contiene 30 requisitos publicados el 13 de septiembre. La correspondencia completa está en REQUISITOS_FORO_GRUPO09.md y los comandos en COMANDOS_CATEGORIAS.md. Es una ampliación del alcance previamente revisado.
