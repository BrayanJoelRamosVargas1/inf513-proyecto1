-- Literal example from section 3 of the official assignment. This is NOT p1_usuario.
create table if not exists persona (
    ci varchar(20) primary key check(ci ~ '^[0-9]+$'),
    nombres varchar(100) not null,
    apellidos varchar(120) not null,
    ocupacion varchar(80) not null,
    telefono varchar(30) not null check(telefono ~ '^[0-9]+$'),
    celular varchar(30) not null check(celular ~ '^[0-9]+$'),
    correo varchar(160) not null unique
);
create table if not exists p1_usuario (
    id bigserial primary key,
    registro varchar(20) unique not null,
    nombres varchar(100) not null,
    apellidos varchar(120) not null,
    rol varchar(20) not null check(rol in('DECANO','VICEDECANO','EVALUADOR','POSTULANTE')),
    telefono varchar(30),
    correo varchar(160) unique not null,
    activo boolean not null default true,
    creado_en timestamptz not null default now()
);
create table if not exists p1_convocatoria (
    id bigserial primary key,
    codigo varchar(30) unique not null,
    titulo varchar(200) not null,
    fecha_inicio date not null,
    fecha_fin date not null,
    estado varchar(20) not null,
    plazas integer not null check(plazas>=0),
    creado_en timestamptz not null default now()
);
create table if not exists p1_cargo (
    id bigserial primary key,
    nombre varchar(160) not null,
    unidad varchar(160),
    activo boolean not null default true
);
create table if not exists p1_oferta (
    convocatoria_id bigint not null references p1_convocatoria,
    cargo_id bigint not null references p1_cargo,
    plazas integer not null check(plazas>=0),
    primary key(convocatoria_id,cargo_id)
);
create table if not exists p1_area_examen (
    convocatoria_id bigint not null,
    cargo_id bigint not null,
    area varchar(80) not null check(area=lower(btrim(area)) and length(area)>0 and area<>'general'),
    peso numeric(5,2) not null check(peso>0 and peso<=100),
    primary key(convocatoria_id,cargo_id,area),
    foreign key(convocatoria_id,cargo_id) references p1_oferta(convocatoria_id,cargo_id)
);
create table if not exists p1_postulacion (
    id bigserial primary key,
    convocatoria_id bigint not null references p1_convocatoria,
    postulante_id bigint not null references p1_usuario,
    cargo_id bigint not null references p1_cargo,
    estado varchar(30) not null default 'REGISTRADA',
    observacion text,
    puntaje_meritos numeric(7,2) default 0,
    puntaje_examen numeric(7,2) default 0,
    puntaje_final numeric(7,2) default 0,
    plaza_asignada boolean not null default false,
    creado_en timestamptz not null default now(),
    unique(convocatoria_id,postulante_id,cargo_id)
);
create table if not exists p1_expediente (
    id bigserial primary key,
    postulacion_id bigint not null references p1_postulacion,
    descripcion varchar(200) not null,
    ruta_archivo text not null,
    tipo_mime varchar(120),
    creado_en timestamptz not null default now()
);
create table if not exists p1_evaluacion_requisito (
    id bigserial primary key,
    postulacion_id bigint not null references p1_postulacion,
    requisito varchar(200) not null,
    cumple boolean not null,
    observacion text,
    evaluador_correo varchar(160) not null,
    fecha timestamptz not null default now()
);
create table if not exists p1_merito (
    id bigserial primary key,
    postulacion_id bigint not null references p1_postulacion,
    criterio varchar(200) not null,
    puntaje numeric(7,2) not null,
    observacion text,
    evaluador_correo varchar(160) not null,
    fecha timestamptz not null default now()
);
create table if not exists p1_examen (
    id bigserial primary key,
    postulacion_id bigint not null references p1_postulacion,
    puntaje numeric(7,2) not null,
    evaluador_correo varchar(160) not null,
    observacion text,
    fecha timestamptz not null default now()
);
alter table p1_examen add column if not exists area varchar(80) not null default 'general';
alter table p1_postulacion add column if not exists evaluacion_version bigint not null default 0;
create table if not exists p1_desempate (
    postulacion_id bigint primary key references p1_postulacion,
    evaluacion_version bigint not null,
    puntaje numeric(7,2) not null,
    prioridad integer not null check(prioridad>0),
    justificacion varchar(500) not null check(length(btrim(justificacion))>=10),
    actor varchar(160) not null,
    fecha timestamptz not null default now()
);
create table if not exists p1_asignacion_evaluador (
    id bigserial primary key,
    postulacion_id bigint not null references p1_postulacion,
    evaluador_id bigint not null references p1_usuario,
    tipo varchar(40) not null,
    unique(postulacion_id,evaluador_id,tipo)
);
create table if not exists p1_auditoria (
    id bigserial primary key,
    fecha timestamptz not null default now(),
    actor varchar(160) not null,
    accion varchar(80) not null,
    entidad varchar(80) not null,
    entidad_id varchar(80),
    detalle text
);
create table if not exists p1_correo_procesado (
    id bigserial primary key,
    mensaje_id varchar(255) unique not null,
    remitente varchar(160) not null,
    asunto text,
    exito boolean not null,
    procesado_en timestamptz not null default now()
);
create table if not exists p1_correo_control (
    mensaje_id varchar(255) primary key,
    remitente varchar(160) not null,
    asunto text,
    estado varchar(30) not null check(estado in('DIFERIDO','RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO')),
    respuesta_asunto text,
    respuesta_cuerpo text,
    exito boolean,
    intentos integer not null default 0,
    ultimo_error text,
    siguiente_intento timestamptz not null default now(),
    creado_en timestamptz not null default now(),
    actualizado_en timestamptz not null default now()
);
alter table p1_correo_control drop constraint if exists p1_correo_control_estado_check;
alter table p1_correo_control add constraint p1_correo_control_estado_check check(estado in('DIFERIDO','RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO'));
alter table p1_correo_control add column if not exists admitido_en timestamptz;
update p1_correo_control set admitido_en=creado_en where admitido_en is null and estado<>'DIFERIDO';
create index if not exists idx_correo_admitido on p1_correo_control(admitido_en,lower(remitente));
create table if not exists p1_confirmacion (
    mensaje_id varchar(255) primary key,
    remitente varchar(160) not null,
    token_hash varchar(64) unique not null,
    orden_json text not null,
    vence_en timestamptz not null default now()+interval '30 minutes',
    confirmacion_id varchar(255),
    usado boolean not null default false
);
create table if not exists p1_resultado_orden (
    mensaje_id varchar(255) primary key,
    exito boolean not null,
    respuesta_asunto text not null,
    respuesta_cuerpo text not null,
    ejecutado_en timestamptz not null default now()
);
create table if not exists p1_servicio_estado (
    clave varchar(80) primary key,
    valor text not null,
    actualizado_en timestamptz not null default now()
);

-- Non-destructive migration for actual PDF upload, separate from legacy file references.
alter table p1_expediente add column if not exists nombre_archivo varchar(180);
alter table p1_expediente add column if not exists contenido bytea;
alter table p1_expediente add column if not exists sha256 char(64);
create table if not exists p1_adjunto_correo (
    mensaje_id varchar(255) primary key references p1_correo_control(mensaje_id),
    nombre varchar(180) not null,
    contenido bytea not null check(octet_length(contenido) between 5 and 2097152),
    sha256 char(64) not null,
    creado_en timestamptz not null default now()
);
drop index if exists idx_usuario_correo;
create unique index if not exists uq_p1_usuario_correo_ci on p1_usuario(lower(correo));
create index if not exists idx_postulacion_convocatoria on p1_postulacion(convocatoria_id);
create index if not exists idx_auditoria_entidad on p1_auditoria(entidad,entidad_id,fecha desc);
create index if not exists idx_correo_control_pendiente on p1_correo_control(estado,siguiente_intento);
create index if not exists idx_correo_control_remitente_fecha on p1_correo_control(lower(remitente),creado_en desc);
create index if not exists idx_correo_control_fecha on p1_correo_control(creado_en desc);
alter table p1_convocatoria drop constraint if exists p1_convocatoria_fechas_ck;
alter table p1_convocatoria add constraint p1_convocatoria_fechas_ck check(fecha_fin>=fecha_inicio) not valid;
alter table p1_merito drop constraint if exists p1_merito_puntaje_ck;
alter table p1_merito add constraint p1_merito_puntaje_ck check(puntaje between 0 and 100) not valid;
alter table p1_examen drop constraint if exists p1_examen_puntaje_ck;
alter table p1_examen add constraint p1_examen_puntaje_ck check(puntaje between 0 and 100) not valid;
alter table p1_postulacion drop constraint if exists p1_postulacion_puntajes_ck;
alter table p1_postulacion add constraint p1_postulacion_puntajes_ck check(puntaje_meritos between 0 and 100 and puntaje_examen between 0 and 100 and puntaje_final between 0 and 100) not valid;
alter table p1_convocatoria validate constraint p1_convocatoria_fechas_ck;
alter table p1_merito validate constraint p1_merito_puntaje_ck;
alter table p1_examen validate constraint p1_examen_puntaje_ck;
alter table p1_postulacion validate constraint p1_postulacion_puntajes_ck;
alter table p1_convocatoria drop constraint if exists p1_convocatoria_estado_ck;
alter table p1_convocatoria add constraint p1_convocatoria_estado_ck check(estado in('BORRADOR','ABIERTA','CERRADA','CANCELADA','FINALIZADA')) not valid;
alter table p1_postulacion drop constraint if exists p1_postulacion_estado_ck;
alter table p1_postulacion add constraint p1_postulacion_estado_ck check(estado in('REGISTRADA','HABILITADO','INHABILITADO','GANADOR')) not valid;
alter table p1_postulacion drop constraint if exists p1_postulacion_plaza_ck;
alter table p1_postulacion add constraint p1_postulacion_plaza_ck check(not plaza_asignada or estado='GANADOR') not valid;
alter table p1_convocatoria validate constraint p1_convocatoria_estado_ck;
alter table p1_postulacion validate constraint p1_postulacion_estado_ck;
alter table p1_postulacion validate constraint p1_postulacion_plaza_ck;

