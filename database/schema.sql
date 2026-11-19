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
    tipo varchar(80) not null,
    unique(postulacion_id,evaluador_id,tipo)
);
alter table p1_asignacion_evaluador alter column tipo type varchar(80);
create table if not exists p1_auditoria (
    id bigserial primary key,
    fecha timestamptz not null default now(),
    actor varchar(160) not null,
    accion varchar(80) not null,
    entidad varchar(80) not null,
    entidad_id varchar(160),
    detalle text
);
alter table p1_auditoria alter column entidad_id type varchar(160);
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
-- Los mensajes históricos quedan RESPONDIDO sin consumir el cupo de recepción.
do $$
begin
    if not exists (
        select 1 from pg_attribute where attrelid='p1_correo_control'::regclass
        and attname='admitido_en' and not attisdropped
    ) then
        alter table p1_correo_control add column admitido_en timestamptz;
        update p1_correo_control set admitido_en=creado_en where estado<>'DIFERIDO';
    end if;

    if to_regclass(format('%I.p1_correo_procesado', current_schema())) is not null then
        execute format(
            'insert into p1_correo_control(mensaje_id,remitente,asunto,estado,exito,creado_en,actualizado_en,siguiente_intento)
             select mensaje_id,remitente,asunto,%L,exito,procesado_en,procesado_en,procesado_en from %I.p1_correo_procesado
             on conflict(mensaje_id) do nothing',
            'RESPONDIDO', current_schema()
        );
    end if;
end
$$;
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

-- Refuerzos de integridad para escrituras directas en PostgreSQL.
create or replace function inf513_validar_cupo_oferta()
returns trigger language plpgsql as $$
declare
    cupo integer;
    otros bigint;
begin
    -- La nueva versión de la fila serializa cambios de cupo incluso con un snapshot anterior.
    update p1_convocatoria set plazas = plazas where id = new.convocatoria_id
    returning plazas into cupo;

    select coalesce(sum(plazas), 0) into otros
    from p1_oferta
    where convocatoria_id = new.convocatoria_id and cargo_id <> new.cargo_id
      and (tg_op = 'INSERT' or (convocatoria_id, cargo_id)
          is distinct from (old.convocatoria_id, old.cargo_id));

    if otros + new.plazas > cupo then
        raise exception 'La suma de las ofertas supera las plazas de la convocatoria'
            using errcode = '23514';
    end if;
    return new;
end
$$;

drop trigger if exists p1_oferta_cupo_trg on p1_oferta;
create trigger p1_oferta_cupo_trg
before insert or update of convocatoria_id, cargo_id, plazas on p1_oferta
for each row execute function inf513_validar_cupo_oferta();

create or replace function inf513_validar_cupo_convocatoria()
returns trigger language plpgsql as $$
begin
    if new.plazas < (select coalesce(sum(plazas), 0) from p1_oferta where convocatoria_id = new.id) then
        raise exception 'Las plazas de la convocatoria son inferiores a las plazas ofertadas'
            using errcode = '23514';
    end if;
    return new;
end
$$;

drop trigger if exists p1_convocatoria_cupo_trg on p1_convocatoria;
create trigger p1_convocatoria_cupo_trg
before update of plazas on p1_convocatoria
for each row when (old.plazas is distinct from new.plazas)
execute function inf513_validar_cupo_convocatoria();

create or replace function inf513_validar_cargo_postulacion()
returns trigger language plpgsql as $$
begin
    perform 1 from p1_convocatoria where id = new.convocatoria_id for update;
    if exists (select 1 from p1_oferta where convocatoria_id = new.convocatoria_id)
       and not exists (
          select 1 from p1_oferta where convocatoria_id = new.convocatoria_id
          and cargo_id = new.cargo_id and plazas > 0
       ) then
        raise exception 'El cargo de la postulación debe tener una oferta con cupo'
            using errcode = '23514';
    end if;
    return new;
end
$$;

drop trigger if exists p1_postulacion_oferta_trg on p1_postulacion;
create trigger p1_postulacion_oferta_trg
before insert or update of convocatoria_id, cargo_id on p1_postulacion
for each row execute function inf513_validar_cargo_postulacion();

create or replace function inf513_validar_total_areas()
returns trigger language plpgsql as $$
declare
    otros numeric;
    convocatoria_anterior bigint;
    cargo_anterior bigint;
    convocatoria_nueva bigint;
    cargo_nuevo bigint;
begin
    if tg_op <> 'INSERT' then
        convocatoria_anterior := old.convocatoria_id;
        cargo_anterior := old.cargo_id;
    end if;
    if tg_op <> 'DELETE' then
        convocatoria_nueva := new.convocatoria_id;
        cargo_nuevo := new.cargo_id;
    end if;

    -- Serializa la configuración y el registro de la primera nota.
    update p1_convocatoria set plazas = plazas where id in (convocatoria_anterior, convocatoria_nueva);
    if exists (
        select 1 from p1_postulacion p join p1_examen e on e.postulacion_id = p.id
        where (p.convocatoria_id, p.cargo_id) in
            ((convocatoria_anterior, cargo_anterior), (convocatoria_nueva, cargo_nuevo))
    ) then
        raise exception 'No se pueden cambiar las ponderaciones después de registrar exámenes'
            using errcode = '23514';
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;

    select coalesce(sum(peso), 0) into otros
    from p1_area_examen
    where convocatoria_id = new.convocatoria_id and cargo_id = new.cargo_id
      and (tg_op = 'INSERT' or (convocatoria_id, cargo_id, area)
          is distinct from (old.convocatoria_id, old.cargo_id, old.area))
      and (convocatoria_id, cargo_id, area)
          is distinct from (new.convocatoria_id, new.cargo_id, new.area);
    if otros + new.peso > 100 then
        raise exception 'Los pesos de las áreas no pueden superar 100'
            using errcode = '23514';
    end if;
    return new;
end
$$;

drop trigger if exists p1_area_total_trg on p1_area_examen;
create trigger p1_area_total_trg
before insert or update or delete on p1_area_examen
for each row execute function inf513_validar_total_areas();

create or replace function inf513_validar_examen()
returns trigger language plpgsql as $$
declare
    convocatoria bigint;
    cargo bigint;
    peso_total numeric;
    area_valida boolean;
begin
    select p.convocatoria_id, p.cargo_id into convocatoria, cargo
    from p1_postulacion p where p.id = new.postulacion_id;
    if not found then return new; end if;
    update p1_convocatoria set plazas = plazas where id = convocatoria;
    select sum(a.peso), coalesce(bool_or(a.area = new.area), false)
    into peso_total, area_valida
    from p1_area_examen a where a.convocatoria_id = convocatoria and a.cargo_id = cargo;

    if (peso_total is null and new.area <> 'general')
       or (peso_total is not null and (peso_total <> 100 or not area_valida)) then
        raise exception 'El área de examen debe existir y su categoría debe sumar 100'
            using errcode = '23514';
    end if;
    if not exists (
        select 1 from p1_asignacion_evaluador a join p1_usuario u on u.id = a.evaluador_id
        where a.postulacion_id = new.postulacion_id and u.activo and u.rol = 'EVALUADOR'
          and lower(u.correo) = lower(new.evaluador_correo)
          and (upper(a.tipo) in ('EXAMEN','MERITOS_Y_EXAMEN')
               or (new.area <> 'general' and upper(a.tipo) <> 'MERITOS' and lower(a.tipo) = new.area))
    ) then
        raise exception 'El examen requiere un evaluador activo asignado'
            using errcode = '23514';
    end if;
    return new;
end
$$;

drop trigger if exists p1_examen_configuracion_trg on p1_examen;
create trigger p1_examen_configuracion_trg
before insert or update of postulacion_id, area, evaluador_correo on p1_examen
for each row execute function inf513_validar_examen();
