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
8 Analizador y validación
8 1 Gramática y límites
8 2 Flujo sintáctico
8 3 Validación semántica y autorización
9 Modelo de base de datos
9 1 Modelo del negocio
9 2 Modelo de soporte del correo
9 3 Diccionario de datos
persona
p1 usuario
p1 convocatoria
p1 cargo
p1 postulacion
p1 expediente
p1 evaluacion requisito
p1 merito
p1 examen
p1 asignacion evaluador
p1 auditoria
p1 correo procesado
p1 correo control
p1 confirmacion
p1 resultado orden
p1 servicio estado
p1 adjunto correo
p1 oferta
p1 requisito config
p1 area examen
p1 asignacion area
p1 nota area
p1 resolucion empate
9 4 Integridad transacciones y migración
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
12 4 Evidencia histórica y trabajo institucional pendiente
13 Instalación configuración y operación
13 1 Construcción y base de datos
13 2 Configuración privada
13 3 Supervisión y operación diaria
14 Guion de defensa
15 Conclusiones
16 Referencias
17 Anexos de entrega
18 Configuración y evaluación por categorías
18 1 Modelo ampliado de categorías
18 2 Matriz de los treinta requisitos del foro
RF-01 Administración de usuarios y roles
RF-02 Convocatorias y gestión
RF-03 Tipos de auxiliatura por convocatoria
RF-04 Plazas por categoría y unidad
RF-05 Alcance académico
RF-06 Requisitos configurables
RF-07 Registro de postulación
RF-08 Postulación múltiple
RF-09 Expediente con varios documentos
RF-10 Documentación obligatoria
RF-11 Revisión digital
RF-12 Cumplimiento de requisitos
RF-13 Habilitación motivada
RF-14 Acceso al examen
RF-15 Formulario CPD de méritos
RF-16 Máximo 40 de méritos
RF-17 Áreas activables
RF-18 Ponderaciones por categoría
RF-19 Docentes por área y tipo
RF-20 Notas por cuenta asignada
RF-21 Examen hasta 60
RF-22 Dos decimales
RF-23 Suma final sobre 100
RF-24 Cálculo independiente
RF-25 Ranking por categoría
RF-26 Selección por cupos y posición
RF-27 Empates en el corte
RF-28 Resultados consolidados
RF-29 Reportes del proceso
RF-30 Auditoría sensible
18 3 Manual de comandos del proceso configurado
Administración
Expediente y habilitación
Evaluación y cálculo
Ranking plazas y reportes
18 4 Evidencia institucional y límite de despliegue