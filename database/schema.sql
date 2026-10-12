create table if not exists p1_usuario(id bigserial primary key,registro varchar(20) unique not null,nombres varchar(100) not null,apellidos varchar(120) not null,rol varchar(20) not null check(rol in('DECANO','VICEDECANO','EVALUADOR','POSTULANTE')),telefono varchar(30),correo varchar(160) unique not null,activo boolean not null default true,creado_en timestamptz not null default now());
create table if not exists p1_convocatoria(id bigserial primary key,codigo varchar(30) unique not null,titulo varchar(200) not null,fecha_inicio date not null,fecha_fin date not null,estado varchar(20) not null,plazas integer not null check(plazas>=0),creado_en timestamptz not null default now());
create table if not exists p1_cargo(id bigserial primary key,nombre varchar(160) not null,unidad varchar(160),activo boolean not null default true);
create table if not exists p1_postulacion(id bigserial primary key,convocatoria_id bigint not null references p1_convocatoria,postulante_id bigint not null references p1_usuario,cargo_id bigint not null references p1_cargo,estado varchar(30) not null default 'REGISTRADA',observacion text,puntaje_meritos numeric(7,2) default 0,puntaje_examen numeric(7,2) default 0,puntaje_final numeric(7,2) default 0,plaza_asignada boolean not null default false,creado_en timestamptz not null default now(),unique(convocatoria_id,postulante_id,cargo_id));
create table if not exists p1_expediente(id bigserial primary key,postulacion_id bigint not null references p1_postulacion,descripcion varchar(200) not null,ruta_archivo text not null,tipo_mime varchar(120),creado_en timestamptz not null default now());
create table if not exists p1_evaluacion_requisito(id bigserial primary key,postulacion_id bigint not null references p1_postulacion,requisito varchar(200) not null,cumple boolean not null,observacion text,evaluador_correo varchar(160) not null,fecha timestamptz not null default now());
create table if not exists p1_merito(id bigserial primary key,postulacion_id bigint not null references p1_postulacion,criterio varchar(200) not null,puntaje numeric(7,2) not null,observacion text,evaluador_correo varchar(160) not null,fecha timestamptz not null default now());
create table if not exists p1_examen(id bigserial primary key,postulacion_id bigint not null references p1_postulacion,puntaje numeric(7,2) not null,evaluador_correo varchar(160) not null,observacion text,fecha timestamptz not null default now());
create table if not exists p1_asignacion_evaluador(id bigserial primary key,postulacion_id bigint not null references p1_postulacion,evaluador_id bigint not null references p1_usuario,tipo varchar(40) not null,unique(postulacion_id,evaluador_id,tipo));
create table if not exists p1_auditoria(id bigserial primary key,fecha timestamptz not null default now(),actor varchar(160) not null,accion varchar(80) not null,entidad varchar(80) not null,entidad_id varchar(80),detalle text);
create table if not exists p1_correo_procesado(id bigserial primary key,mensaje_id varchar(255) unique not null,remitente varchar(160) not null,asunto text,exito boolean not null,procesado_en timestamptz not null default now());
create table if not exists p1_correo_control(
  mensaje_id varchar(255) primary key,
  remitente varchar(160) not null,
  asunto text,
  estado varchar(30) not null check(estado in('RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO')),
  respuesta_asunto text,
  respuesta_cuerpo text,
  exito boolean,
  intentos integer not null default 0,
  ultimo_error text,
  siguiente_intento timestamptz not null default now(),
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create table if not exists p1_resultado_orden(
  mensaje_id varchar(255) primary key,
  exito boolean not null,
  respuesta_asunto text not null,
  respuesta_cuerpo text not null,
  ejecutado_en timestamptz not null default now()
);
create table if not exists p1_servicio_estado(clave varchar(80) primary key,valor text not null,actualizado_en timestamptz not null default now());
create index if not exists idx_usuario_correo on p1_usuario(lower(correo));
create index if not exists idx_postulacion_convocatoria on p1_postulacion(convocatoria_id);
create index if not exists idx_auditoria_entidad on p1_auditoria(entidad,entidad_id,fecha desc);
create index if not exists idx_correo_control_pendiente on p1_correo_control(estado,siguiente_intento);

