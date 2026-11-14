# Ensayo del Proyecto 1 del Grupo 09 SA

## Preparación antes de la defensa

Usamos tres cuentas distintas: DECANO, EVALUADOR y POSTULANTE. El DECANO crea las otras dos y confirma cada alta desde su propio correo. La cuenta del EVALUADOR debe poder enviar sus propias órdenes; la del POSTULANTE debe poder enviar las suyas. No hacen falta contraseñas de Gmail dentro del programa.

Las cuentas de demostración usan registros ficticios 990900001 y 990900002 y nombres que indican que son de prueba. No representan a personas postuladas a una convocatoria real. Las contraseñas y los códigos activos nunca deben mostrarse durante la presentación.

El servicio Java debe estar encendido antes de enviar órdenes. Si se reinicia el equipo, arrancar con el manual. No iniciar dos instancias. Una caída de Internet impide recibir o enviar correo hasta recuperar la conexión; la cola permite reintentar después.

## Regla de confirmación

Todas las órdenes se envían en el asunto de un correo nuevo a la cuenta institucional del grupo. AYUDA, LISCON y LISCAR responden directamente. Las demás generan un correo con una orden CONFIRMAR y un código: enviarlo desde la misma cuenta solicitante antes de 30 minutos. Copiar el código del mensaje más reciente y revisar la orden pendiente antes de aprobarla. Esperar el resultado antes de pasar al paso que depende de él.

## Recorrido del ensayo

Los números de ejemplo no son IDs reales: reemplazar los marcadores por los que devuelve la base. No pegar los marcadores como órdenes. Para crear convocatoria y cargo se utilizan estos asuntos completos desde DECANO:

```text
INSCON["DEMO-09SA-20261014","Demostración académica de auxiliaturas","2026-10-13","2026-10-20","ABIERTA","1"]
INSCAR["Auxiliar de demostración 09SA","Ensayo académico","true"]
```

No repetir esas altas si ya existen. Localizar los IDs con LISCON["DEMO-09SA-20261014"] y LISCAR["demostración 09SA"]. Preparar un PDF de expediente ficticio sin datos personales y usar una ruta o enlace que realmente exista. INSPOST registra la referencia del expediente; no transfiere por sí mismo un archivo adjunto de Gmail.

| Paso | Cuenta remitente | Orden o acción | Evidencia esperada |
| --- | --- | --- | --- |
| 1 | Cualquiera | AYUDA[] | Catálogo recibido |
| 2 | DECANO | LISUSU["*"] y confirmación | Las tres cuentas con sus roles |
| 3 | DECANO | INSCON e INSCAR anteriores, confirmar cada una | Convocatoria vigente y cargo activo |
| 4 | Cualquiera | LISCON y LISCAR con los filtros anteriores | IDs de convocatoria y cargo |
| 5 | POSTULANTE | INSPOST["ID_CONVOCATORIA","ID_CARGO","RUTA_REAL_DEL_PDF"] | Postulación y referencia del expediente |
| 6 | POSTULANTE | LISPOST["*"] | ID de postulación |
| 7 | DECANO | ASIEVA["ID_POSTULACION","ID_USUARIO_EVALUADOR","MERITOS_Y_EXAMEN"] | Evaluador asignado |
| 8 | EVALUADOR | EVAREQ["ID_POSTULACION","Expediente de ensayo","true","Documentación ficticia revisada"] | Requisito registrado |
| 9 | DECANO | HABPOS["ID_POSTULACION","HABILITADO","Cumple requisitos del ensayo"] | Habilitación |
| 10 | EVALUADOR | REGMER["ID_POSTULACION","Mérito de ensayo","80","Calificación ficticia"] | Mérito 80 |
| 11 | EVALUADOR | REGEXA["ID_POSTULACION","85","Examen ficticio revisado"] | Examen 85 y resultado final 83 |
| 12 | DECANO | GENRAN["ID_CONVOCATORIA"] | Ranking |
| 13 | DECANO | ASIPLA["ID_POSTULACION","true"] | Plaza de ensayo asignada |
| 14 | DECANO | AUDCAL["ASIPLA","ID_POSTULACION"] | Actor y acción registrados |
| 15 | DECANO | REPEST["ID_CONVOCATORIA"] | Estadísticas de la convocatoria de ensayo |

Todas las filas privadas requieren confirmar. La ponderación 40 % de méritos y 60 % de examen es la regla implementada en este proyecto, no una afirmación sobre una normativa de la Facultad.

## Distribución de los 15 a 20 minutos

Explicar objetivo y arquitectura en los primeros cuatro minutos. Mostrar una consulta pública y una operación confirmada durante los siguientes cinco. Continuar con la postulación y evaluación ya preparadas, ranking y auditoría. Reservar los últimos minutos para una entrada incorrecta, preguntas y conclusiones. Tener datos de ensayo previos permite mostrar el recorrido sin depender de que todos los correos lleguen inmediatamente durante la defensa.

## Pruebas que haremos en el ensayo detallado

Comprobar primero el recorrido correcto. Después probar un número escrito con letras, un puntaje mayor que 100, un comando inexistente, una orden enviada desde el rol equivocado, una confirmación reutilizada y examen antes de habilitación. Ninguno debe producir un cambio indebido. Ensayar cortes solo en un entorno aislado o en este cliente controlado, sin tumbar servicios compartidos de la Facultad.

## Entrega y material del grupo

Subir el ZIP que contiene informe y código, no únicamente el PDF. Conservar el TAR.GZ como formato alternativo indicado en la consigna. Compartir el ZIP y las diapositivas con los compañeros, revisar este guion e imprimir el informe. El repositorio personal debe coincidir con el paquete. No declarar completada la entrega hasta que la plataforma confirme el envío en la actividad correcta.
