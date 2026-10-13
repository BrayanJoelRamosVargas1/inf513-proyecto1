# Manual de ejecución y defensa

## Preparación

1. Instalar Java 17.
2. Abrir PowerShell en la carpeta del proyecto.
3. Definir las credenciales como variables de entorno temporales, sin guardarlas en archivos ni subirlas a GitHub:

```powershell
$env:MAIL_PASSWORD = "ESCRIBIR_LA_CLAVE_SOLO_EN_LA_TERMINAL"
$env:DB_PASSWORD = "ESCRIBIR_LA_CLAVE_SOLO_EN_LA_TERMINAL"
$env:BOOTSTRAP_ADMIN_EMAIL = "TU_CORREO@gmail.com"
java -jar target/inf513-proyecto1-1.0.0.jar
```

No mostrar la terminal mientras se escriben las claves y cerrar la ventana al terminar.

## Primera inicialización

Si `p1_usuario` está vacía, el primer correo debe crear un usuario con rol `DECANO`. El remitente y el correo registrado deben coincidir exactamente con `BOOTSTRAP_ADMIN_EMAIL`; así un desconocido no puede apropiarse del primer usuario.

```text
INSUSU["223043982","Brayan Joel","Ramos Vargas","DECANO","70000000","TU_CORREO@gmail.com","true"]
```

Después pueden crearse los demás roles mediante `INSUSU`.

Las operaciones privadas y los cambios requieren confirmar cada orden. El sistema envía a la cuenta solicitante un mensaje con `CONFIRMAR["codigo"]`; copie ese asunto y envíelo desde la misma cuenta. El código vence en 30 minutos, funciona una vez y solo autoriza la orden indicada en el mensaje. La creación del primer DECANO usa este mismo paso. Compruebe también Spam y no publique códigos activos. Las consultas públicas `AYUDA`, `LISCON` y `LISCAR` responden directamente.

Antes de utilizar esta versión en una instalación anterior: detener Java, aplicar `database/schema.sql` y sustituir el JAR por `dist/inf513-proyecto1-1.0.0.jar`. En el paquete descargado puede ejecutar `java -cp dist/inf513-proyecto1-1.0.0.jar bo.edu.uagrm.inf513.grupo09sa.AplicarEsquema` y luego el supervisor correspondiente al sistema operativo.

## Secuencia de demostración

```text
AYUDA[]
INSCON["AUX-2026-2","Convocatoria de auxiliaturas","2026-10-01","2026-10-20","ABIERTA","3"]
LISCON["*"]
INSCAR["Auxiliar de docencia","Facultad","true"]
LISCAR["*"]
INSPOST["1","1","https://ejemplo.com/expediente.pdf"]
LISPOST["*"]
ASIEVA["1","2","MERITOS_Y_EXAMEN"]
EVAREQ["1","Documento de identidad","true","Documento vigente"]
HABPOS["1","HABILITADO","Cumple requisitos"]
REGMER["1","Experiencia","80","Documentación revisada"]
REGEXA["1","85","Examen revisado"]
GENRAN["1"]
ASIPLA["1","true"]
AUDCAL["ASIPLA","1"]
REPEST["1"]
```

Los identificadores dependen de los registros existentes en PostgreSQL. Consultar primero los listados y reemplazar los valores de ejemplo.

En el paquete descargado use `dist/inf513-proyecto1-1.0.0.jar` si no ha compilado. Prepare previamente cuentas con roles DECANO, EVALUADOR y POSTULANTE y use el remitente correspondiente a cada orden. Asigne el evaluador con ASIEVA antes de EVAREQ. Después evalúe requisitos, habilite la postulación y registre su examen. No use fechas vencidas: sustituya las fechas de INSCON por el periodo vigente de la demostración. Confirme cada operación privada antes de continuar con la siguiente.

## Cierre de la demostración

- Mostrar el correo de respuesta.
- Mostrar el registro correspondiente en PostgreSQL.
- Consultar `AUDCAL` para evidenciar trazabilidad.
- Detener el programa con `Ctrl+C`.
- Eliminar únicamente los datos y mensajes creados para la prueba si el docente lo solicita.

## Prueba de resistencia sugerida

1. Iniciar el servicio y enviar una orden válida.
2. Desconectar temporalmente la red después de que la operación quede registrada.
3. Mostrar que Java permanece activo y anuncia el próximo reintento.
4. Reconectar la red y comprobar que la respuesta se entrega sin repetir la operación.
5. Ejecutar dos veces el procesamiento del mismo UIDL y comprobar `p1_resultado_orden`.

En Windows use `scripts/run-service.ps1`. En Fedora/Linux use `scripts/run-service.sh`; para ejecución permanente puede instalarse `deploy/inf513-grupo09sa.service` con systemd.

## Comprobación ya realizada

Se probó el flujo real desde una cuenta Gmail hacia `grupo09sa@tecnoweb.org.bo`. El programa Java leyó `AYUDA[]` y `LISCON["*"]`, consultó PostgreSQL y envió las respuestas al remitente mediante el Roundcube institucional. Gmail clasificó esas respuestas como correo no deseado durante la prueba; por eso, en la defensa conviene revisar también la carpeta Spam.

Esta comprobación no sustituye la demostración de todos los casos de uso con los datos que el grupo decida presentar.
