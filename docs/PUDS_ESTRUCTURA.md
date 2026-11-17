# Estructura del PUDS

Resumen ejecutivo
Cómo revisar este documento
Índice
1 Introducción
1 1 Contexto y problema
1 2 Objetivo general
1 3 Objetivos específicos
1 4 Alcance y exclusiones
2 Fuentes y organización del trabajo
3 Fundamentos técnicos
4 Requisitos del sistema
4 1 Requisitos no funcionales
4 2 Reglas de negocio y validación
5 Actores y casos de uso
5 1 CU1 Gestión de usuarios
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 2 CU2 Parametrizar convocatorias de auxiliaturas
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 3 CU3 Postulación múltiple y expediente digital
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 4 CU4 Evaluar requisitos y habilitar postulantes
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 5 CU5 Méritos y examen de competencia
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 6 CU6 Resultado final ranking y plazas
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 7 CU7 Proceso y auditoría de calificaciones
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
5 8 CU8 Reportes y estadísticas
Flujo principal
Alternativas y excepciones
Realización en el código
Criterios de aceptación
6 Análisis de arquitectura
6 1 Arquitectura lógica
6 2 Arquitectura física
7 Diseño del sistema
7 1 Estructura de clases
7 2 Ciclo de vida de la cola
7 3 Habilitación y acceso al examen
7 4 Realización UML de los ocho casos
7 4 1 CU1 Gestión de usuarios
CU1 Colaboración y clases participantes
CU1 Diseño de clases y persistencia
7 4 2 CU2 Parametrizar convocatorias
CU2 Colaboración y clases participantes
CU2 Diseño de clases y persistencia
7 4 3 CU3 Postulación y expediente
CU3 Colaboración y clases participantes
CU3 Diseño de clases y persistencia
7 4 4 CU4 Requisitos y habilitación
CU4 Colaboración y clases participantes
CU4 Diseño de clases y persistencia
7 4 5 CU5 Méritos y examen
CU5 Colaboración y clases participantes
CU5 Diseño de clases y persistencia
7 4 6 CU6 Ranking y plazas
CU6 Colaboración y clases participantes
CU6 Diseño de clases y persistencia
7 4 7 CU7 Proceso y auditoría
CU7 Colaboración y clases participantes
CU7 Diseño de clases y persistencia
7 4 8 CU8 Reportes y estadísticas
CU8 Colaboración y clases participantes
CU8 Diseño de clases y persistencia
8 Analizador y validación
8 1 Gramática y límites
8 2 Flujo sintáctico
8 3 Validación semántica y autorización
9 Modelo de base de datos
9 1 Diseño conceptual del negocio
9 2 Auditoría y persistencia del correo
9 3 Mapeo de clases a tablas
9 4 Diccionario de datos
persona
p1 usuario
p1 convocatoria
p1 cargo
p1 oferta
p1 area examen
p1 postulacion
p1 expediente
p1 evaluacion requisito
p1 merito
p1 examen
p1 desempate
p1 asignacion evaluador
p1 auditoria
p1 correo procesado
p1 correo control
p1 confirmacion
p1 resultado orden
p1 servicio estado
p1 adjunto correo
Restricciones y cardinalidades del modelo
9 5 Integridad transacciones y migración
10 Catálogo de comandos
10 1 Ejemplos de la actividad oficial
10 2 Ejemplo de postulación con archivo
11 Seguridad y recuperación
11 1 Autorización e información privada
11 2 Bitácoras y auditoría
11 3 Fallos y límites del servicio
12 Verificación y resultados
12 1 Entorno y resultado reproducible
12 2 Matriz de comprobación
12 3 Caso numérico verificable
12 4 Comprobación institucional de esta versión
13 Instalación configuración y operación
13 1 Construcción y base de datos
13 2 Configuración privada
13 3 Supervisión y operación diaria
14 Guion de defensa
15 Conclusiones
16 Referencias
17 Anexos de entrega
17 1 Script SQL completo
17 2 Trazabilidad de la actualización documental
Ampliación de los ocho casos aprobados
Cupos por categoría
Ponderaciones por área
Resolución motivada de empates
Reportes y persistencia
Verificación y límites de seguridad
