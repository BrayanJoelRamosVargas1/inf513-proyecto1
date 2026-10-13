# Entrega del Proyecto 1 Grupo 09 SA

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
