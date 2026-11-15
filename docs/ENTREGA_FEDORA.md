# Entrega y ejecución en Fedora — 15 de noviembre de 2026

## Qué quedó en el servidor institucional

La última comprobación exitosa del 14 de noviembre confirmó 19 tablas p1_ en
db_grupo09sa, una instancia conectada y cero correos pendientes. Se aplicó la
migración de ofertas por cargo, áreas de examen y desempates. Los ensayos de
correo registraron respuestas de éxito, rechazo de duplicados y órdenes
inválidas. Se retiraron los datos ficticios indicados en las evidencias; el
historial y la auditoría correspondientes se conservaron. No se afirma que
los datos de ensayo constituyan un proceso real de convocatoria completado.

El programa Java no se instaló en el servidor de la Facultad. Se ejecuta en
la PC y se conecta a PostgreSQL institucional y al correo por Roundcube HTTPS.
No se dispone de administración de la infraestructura. El fallo DNS del 15
impide comprobar el estado actual de la base y el correo. El ejecutable del
14 también falló al repetir sus sondas con las mismas credenciales.

## Archivos de la entrega

- `entrega/PUDS_INF513_P1_Grupo09SA.docx`: documento editable.
- `entrega/PUDS_INF513_P1_Grupo09SA.pdf`: documento para lectura/impresión.
- `src/`, `pom.xml`, `database/`: fuente Java, compilación y esquema.
- `dist/inf513-proyecto1-1.0.0.jar`: ejecutable ya compilado, Java 17 o superior.
- `scripts/run-service.sh`: arranque manual en Linux.
- `deploy/inf513-grupo09sa.service`: servicio automático para Linux.
- `docs/`, `evidencias/`: manuales, controles, límites y verificaciones posteriores.

El PUDS refleja la revisión funcional de 111 pruebas; las correcciones y pruebas
posteriores de seguridad se documentan en los informes incluidos, hasta 122
pruebas. El alcance académico sigue siendo los ocho CU aprobados. No se declara
ausencia de toda vulnerabilidad. El PostgreSQL institucional no ofrece TLS.

## En esta PC con arranque dual

La preparación privada y las claves ya están fuera del comprimido público.
Desde Fedora, abrir la partición de Windows y una terminal en
`Users/ElCremosasWee/Downloads`. Ejecutar:

```sh
bash INSTALAR_PROYECTO_FEDORA.sh
```

Conservar el directorio original `Documents/ChatGPT/fedora_linux_tecno_web`,
porque el iniciador encuentra allí la preparación privada. Se pedirá la
contraseña local de Fedora para instalar Java, ClamAV y el servicio. Las claves
institucionales no se muestran ni deben publicarse. La instalación se detiene
si falla la comprobación de PostgreSQL, correo o antivirus. Mientras continúe
el fallo DNS, no se acredita un despliegue Fedora funcional completo.

## Arranque manual desde el comprimido en Linux

Extraer el TAR.GZ y entrar en su carpeta `inf513-proyecto1`. Instalar Java 17
o superior, ClamAV y sus firmas usando los paquetes de la distribución.
La configuración de ejemplo ya apunta a los servicios institucionales:

```sh
cp config/application.example.properties config/application.properties
read -r -s -p 'Clave del correo: ' MAIL_PASSWORD; printf '\n'
read -r -s -p 'Clave de PostgreSQL: ' DB_PASSWORD; printf '\n'
export MAIL_PASSWORD DB_PASSWORD
export PDF_ANTIVIRUS=clamav
sh scripts/run-service.sh
```

Los comandos `read -s -p` requieren Bash. Las claves no se escriben en el
historial. No ejecutar simultáneamente otra instancia conectada a la misma
casilla. No aplicar `database/schema.sql` sobre el servidor compartido para
arrancar: la migración ya fue aplicada el 14. Sin el antivirus disponible y
actualizado se rechazan PDF; sin red y servicios institucionales no se recibe
ni envía correo. El servidor debe ofrecer una conexión verificable para
completar el ensayo real y la prueba después de reiniciar Fedora.

Los scripts `.sh` y unidades `.service` se entregan con finales de línea LF.
`.gitattributes` mantiene ese formato al descargar el repositorio en Windows.
