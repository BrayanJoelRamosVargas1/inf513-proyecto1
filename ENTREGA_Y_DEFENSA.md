# Ampliación por los treinta requisitos del foro

La suite final verifica 79 pruebas sin fallos, errores ni omisiones. Incluye categorías, ponderaciones, documentos obligatorios, revisión de PDFs, empates, corrección de notas y reportes. El transporte institucional del PDF se probó por separado con envío a la misma casilla y comparación exacta de bytes; la migración y el servicio de negocio institucional siguen pendientes por el rechazo automático del respaldo. Consulte docs/REQUISITOS_FORO_GRUPO09.md y docs/COMANDOS_CATEGORIAS.md.

# Entrega del Proyecto 1 Grupo 09 SA

## Versión revisada el 14 de noviembre de 2026

Utilice los documentos PUDS Word y PDF actualizados de `entrega/`, el JAR recién construido de `dist/` y el nuevo paquete `2026-2_INF513-P1_grupo09sa.zip`. La fuente oficial consultada muestra cierre el **20 de noviembre de 2026 a las 15:00** y exige el PUDS impreso el día de la defensa. No se realizó ningún envío a la plataforma.

La revisión se respalda con **79 pruebas locales aprobadas, sin omisiones**. Incluye `persona` con `LISPER` e `INSPER`, logs locales y carga real del PDF en PostgreSQL. Consulte `docs/CAMBIOS_Y_DEFENSA.md` para el recorrido actualizado. La presentación PowerPoint heredada del repositorio es material anterior y no reemplaza el documento ni la evidencia nueva.

Antes de defender falta migrar y probar esta versión en la infraestructura institucional con las cuentas del grupo. El informe identifica expresamente esa tarea pendiente. No use un resultado local como si fuera una captura de Gmail o una confirmación del servidor.

Los apartados que siguen conservan las instrucciones e historial anteriores; donde indiquen 50 pruebas o funcionalidades de octubre, prevalecen los resultados y alcance de esta revisión.

## Archivos para la plataforma

El archivo `2026-2_INF513-P1_grupo09sa.zip` contiene el informe PUDS en PDF y Word, el código fuente, el esquema PostgreSQL, el JAR ejecutable, la presentación y los manuales. También se incluye una copia equivalente `.tar.gz`, porque la consigna menciona ambos formatos. En la plataforma adjunte el ZIP y, si admite un segundo archivo, el TAR.GZ. No entregue únicamente el PDF: esta consigna pide documento y código comprimidos.

El repositorio personal es https://github.com/BrayanJoelRamosVargas1/inf513-proyecto1. Imprima el PDF PUDS antes de la defensa. Las contraseñas no forman parte de la entrega.

## Material para los compañeros

Comparta el comprimido y la presentación. Revisen `MANUAL_DEFENSA.md`, preparen las cuentas DECANO, EVALUADOR y POSTULANTE y adapten fechas e IDs a los registros reales. Cada operación sensible necesita enviar el asunto CONFIRMAR que llega al correo correspondiente. Las consultas públicas responden directamente. La defensa dura 15 a 20 minutos.

## Arranque de la versión actualizada

Desde la carpeta raíz del paquete, configure MAIL_PASSWORD y DB_PASSWORD en el entorno privado. Configure BOOTSTRAP_ADMIN_EMAIL solo para crear el primer DECANO. Detenga la instancia anterior antes de migrar. Aplique el esquema con:

```
java -cp dist/inf513-proyecto1-1.0.0.jar bo.edu.uagrm.inf513.grupo09sa.AplicarEsquema
```

Luego ejecute `scripts/run-service.ps1` en Windows o `sh scripts/run-service.sh` en Linux. Java 17 basta para usar el ejecutable entregado. No se necesita una interfaz web para este proyecto de correo.

## Estado de comprobación

Código y JAR probados localmente con 50 pruebas automatizadas y PostgreSQL 15 aislado, incluido el recorrido de los ocho casos de uso y fallos simulados de lectura, envío y marcado del correo. El 13 de octubre de 2026 autenticamos en PostgreSQL 18 y en el correo institucional, respaldamos los datos p1_ y aplicamos la migración. El servicio Java se inició en este equipo Windows, conectado a los servicios institucionales; no se afirma que Java esté instalado ni ejecutándose en el servidor de la Facultad. El primer DECANO ya se registró mediante una confirmación real recibida desde su Gmail y el servicio envió la respuesta de éxito. La preparación de EVALUADOR y POSTULANTE y el ensayo externo de los demás casos de uso siguen pendientes de confirmación. El equipo que ejecuta Java debe permanecer encendido y conectado.

Los cortes se recuperan mediante cola y reintentos cuando regresan los servicios. Ningún programa puede enviar correo o consultar una base inaccesible mientras dure el corte.
