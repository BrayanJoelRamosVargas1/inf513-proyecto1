-- Instalación del Parcial 1 en un esquema vacío. PostgreSQL 15.

create table if not exists persona (
    ci varchar(20) primary key check(ci ~ '^[0-9]+$'),
    nombres varchar(100) not null,
    apellidos varchar(120) not null,
    ocupacion varchar(80) not null,
    telefono varchar(30) not null check(telefono ~ '^[0-9]+$'),
    celular varchar(30) not null check(celular ~ '^[0-9]+$'),
    correo varchar(160) not null unique
);

create table if not exists usuario (
    id bigserial primary key,
    registro varchar(20) unique not null,
    nombres varchar(100) not null,
    apellidos varchar(120) not null,
    rol varchar(20) not null check(rol in('DECANO','VICEDECANO','EVALUADOR','POSTULANTE')),
    telefono varchar(30),
    correo varchar(160) not null,
    activo boolean not null default true,
    creado_en timestamptz not null default now()
);

create table if not exists convocatoria (
    id bigserial primary key,
    codigo varchar(30) unique not null,
    titulo varchar(200) not null,
    fecha_inicio date not null,
    fecha_fin date not null,
    estado varchar(20) not null,
    plazas integer not null check(plazas>=0),
    creado_en timestamptz not null default now(),
    constraint convocatoria_fechas_ck check (fecha_fin>=fecha_inicio),
    constraint convocatoria_estado_ck check (estado in('BORRADOR','ABIERTA','CERRADA','CANCELADA','FINALIZADA'))
);

create table if not exists cargo (
    id bigserial primary key,
    nombre varchar(160) not null,
    unidad varchar(160),
    activo boolean not null default true
);

create table if not exists oferta (
    convocatoria_id bigint not null references convocatoria,
    cargo_id bigint not null references cargo,
    plazas integer not null check(plazas>=0),
    primary key(convocatoria_id,cargo_id)
);

create table if not exists area_examen (
    convocatoria_id bigint not null,
    cargo_id bigint not null,
    area varchar(80) not null check(area=lower(btrim(area)) and length(area)>0 and area<>'general'),
    peso numeric(5,2) not null check(peso>0 and peso<=100),
    primary key(convocatoria_id,cargo_id,area),
    foreign key(convocatoria_id,cargo_id) references oferta(convocatoria_id,cargo_id)
);

create table if not exists postulacion (
    id bigserial primary key,
    convocatoria_id bigint not null references convocatoria,
    postulante_id bigint not null references usuario,
    cargo_id bigint not null references cargo,
    estado varchar(30) not null default 'REGISTRADA',
    observacion text,
    puntaje_meritos numeric(7,2) default 0,
    puntaje_examen numeric(7,2) default 0,
    puntaje_final numeric(7,2) default 0,
    plaza_asignada boolean not null default false,
    creado_en timestamptz not null default now(),
    unique(convocatoria_id,postulante_id,cargo_id),
    constraint postulacion_puntajes_ck check (puntaje_meritos between 0 and 100 and puntaje_examen between 0 and 100 and puntaje_final between 0 and 100),
    constraint postulacion_estado_ck check (estado in('REGISTRADA','HABILITADO','INHABILITADO','GANADOR')),
    constraint postulacion_plaza_ck check (not plaza_asignada or estado='GANADOR'),
    evaluacion_version bigint not null default 0
);

create table if not exists expediente (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    descripcion varchar(200) not null,
    ruta_archivo text not null,
    tipo_mime varchar(120),
    creado_en timestamptz not null default now(),
    nombre_archivo varchar(180),
    contenido bytea,
    sha256 char(64)
);

create table if not exists evaluacion_requisito (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    requisito varchar(200) not null,
    cumple boolean not null,
    observacion text,
    evaluador_id bigint not null references usuario,
    fecha timestamptz not null default now()
);

create table if not exists merito (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    criterio varchar(200) not null,
    puntaje numeric(7,2) not null,
    observacion text,
    evaluador_id bigint not null references usuario,
    fecha timestamptz not null default now(),
    constraint merito_puntaje_ck check (puntaje between 0 and 100)
);

create table if not exists examen (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    puntaje numeric(7,2) not null,
    evaluador_id bigint not null references usuario,
    observacion text,
    fecha timestamptz not null default now(),
    constraint examen_puntaje_ck check (puntaje between 0 and 100),
    area varchar(80) not null default 'general'
);

create table if not exists desempate (
    postulacion_id bigint primary key references postulacion,
    evaluacion_version bigint not null,
    puntaje numeric(7,2) not null,
    prioridad integer not null check(prioridad>0),
    justificacion varchar(500) not null check(length(btrim(justificacion))>=10),
    actor varchar(160) not null,
    fecha timestamptz not null default now()
);

create table if not exists asignacion_evaluador (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    evaluador_id bigint not null references usuario,
    tipo varchar(80) not null,
    unique(postulacion_id,evaluador_id,tipo)
);

create table if not exists buzon (
    protocolo varchar(80) primary key,
    inicializado boolean not null default false,
    actualizado_en timestamptz not null default now()
);

create table if not exists correo_control (
    mensaje_id varchar(255) primary key,
    remitente varchar(160) not null,
    asunto text,
    estado varchar(30) not null,
    respuesta_asunto text,
    respuesta_cuerpo text,
    exito boolean,
    intentos integer not null default 0,
    ultimo_error text,
    siguiente_intento timestamptz not null default now(),
    creado_en timestamptz not null default now(),
    actualizado_en timestamptz not null default now(),
    constraint correo_control_estado_check check (estado in('DIFERIDO','RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO','INTERNO')),
    token_hash varchar(64),
    orden_json text,
    vence_en timestamptz,
    confirmacion_id varchar(255),
    usado boolean not null default false,
    resultado_exito boolean,
    resultado_asunto text,
    resultado_cuerpo text,
    ejecutado_en timestamptz,
    admitido_en timestamptz,
    buzon_protocolo varchar(80) references buzon(protocolo)
);

create table if not exists adjunto_correo (
    mensaje_id varchar(255) primary key references correo_control(mensaje_id),
    nombre varchar(180) not null,
    contenido bytea not null check(octet_length(contenido) between 5 and 2097152),
    sha256 char(64) not null,
    creado_en timestamptz not null default now()
);

create table if not exists auditoria (
    id bigserial primary key,
    fecha timestamptz not null default now(),
    actor varchar(160) not null,
    accion varchar(80) not null,
    entidad varchar(80) not null,
    entidad_id varchar(160),
    detalle text,
    mensaje_id varchar(255) references correo_control(mensaje_id) on delete set null,
    postulacion_id bigint references postulacion(id) on delete set null,
    persona_ci varchar(20) references persona(ci) on delete set null,
    constraint auditoria_referencia_ck check (
        (persona_ci is null or (entidad = 'INSPER' and persona_ci is not distinct from entidad_id))
        and (postulacion_id is null or (entidad <> 'INSPER'
             and (entidad not in ('EVAREQ','HABPOS','REGMER','REGEXA','REGAREA','ASIEVA','ASIPLA')
                  or postulacion_id::text is not distinct from ltrim(ltrim(entidad_id,'+'),'0'))))
    )
);

insert into buzon(protocolo) values ('pop3'),('imap'),('imaps'),('roundcube')
on conflict(protocolo) do nothing;

create unique index if not exists uq_correo_confirmacion on correo_control(token_hash) where token_hash is not null;
create index if not exists idx_correo_admitido on correo_control(admitido_en,lower(remitente));
create unique index if not exists uq_usuario_correo_ci on usuario(lower(correo));
create index if not exists idx_postulacion_convocatoria on postulacion(convocatoria_id);
create index if not exists idx_auditoria_entidad on auditoria(entidad,entidad_id,fecha desc);
create index if not exists idx_correo_control_pendiente on correo_control(estado,siguiente_intento);
create index if not exists idx_correo_control_remitente_fecha on correo_control(lower(remitente),creado_en desc);
create index if not exists idx_correo_control_fecha on correo_control(creado_en desc);
create index if not exists idx_evaluacion_requisito_evaluador on evaluacion_requisito(evaluador_id);
create index if not exists idx_merito_evaluador on merito(evaluador_id);
create index if not exists idx_examen_evaluador on examen(evaluador_id);

-- Refuerzos de integridad para escrituras directas en PostgreSQL.
create or replace function inf513_validar_cupo_oferta()
returns trigger language plpgsql as $$
declare
    cupo integer;
    otros bigint;
begin
    -- La nueva versión de la fila serializa cambios de cupo incluso con un snapshot anterior.
    update convocatoria set plazas = plazas where id = new.convocatoria_id
    returning plazas into cupo;

    select coalesce(sum(plazas), 0) into otros
    from oferta
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

create or replace trigger oferta_cupo_trg
before insert or update of convocatoria_id, cargo_id, plazas on oferta
for each row execute function inf513_validar_cupo_oferta();

create or replace function inf513_validar_cupo_convocatoria()
returns trigger language plpgsql as $$
begin
    if new.plazas < (select coalesce(sum(plazas), 0) from oferta where convocatoria_id = new.id) then
        raise exception 'Las plazas de la convocatoria son inferiores a las plazas ofertadas'
            using errcode = '23514';
    end if;
    return new;
end
$$;

create or replace trigger convocatoria_cupo_trg
before update of plazas on convocatoria
for each row when (old.plazas is distinct from new.plazas)
execute function inf513_validar_cupo_convocatoria();

create or replace function inf513_validar_cargo_postulacion()
returns trigger language plpgsql as $$
begin
    if tg_op = 'UPDATE' and (old.convocatoria_id,old.cargo_id) is distinct from (new.convocatoria_id,new.cargo_id) then
        perform 1 from convocatoria where id in (old.convocatoria_id,new.convocatoria_id) order by id for update;
        if exists (select 1 from examen where postulacion_id=old.id) then
            raise exception 'La convocatoria y el cargo no pueden cambiar después de registrar un examen'
                using errcode='23514';
        end if;
    else
        perform 1 from convocatoria where id = new.convocatoria_id for update;
    end if;
    if exists (select 1 from oferta where convocatoria_id = new.convocatoria_id)
       and not exists (
          select 1 from oferta where convocatoria_id = new.convocatoria_id
          and cargo_id = new.cargo_id and plazas > 0
       ) then
        raise exception 'El cargo de la postulación debe tener una oferta con cupo'
            using errcode = '23514';
    end if;
    return new;
end
$$;

create or replace trigger postulacion_oferta_trg
before insert or update of convocatoria_id, cargo_id on postulacion
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
    update convocatoria set plazas = plazas where id in (convocatoria_anterior, convocatoria_nueva);
    if exists (
        select 1 from postulacion p join examen e on e.postulacion_id = p.id
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
    from area_examen
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

create or replace trigger area_total_trg
before insert or update or delete on area_examen
for each row execute function inf513_validar_total_areas();

create or replace function inf513_validar_examen()
returns trigger language plpgsql as $$
declare
    id_convocatoria_examen bigint;
    id_cargo_examen bigint;
    peso_total numeric;
    area_valida boolean;
begin
    select p.convocatoria_id, p.cargo_id into id_convocatoria_examen, id_cargo_examen
    from postulacion p where p.id = new.postulacion_id;
    if not found then return new; end if;
    update convocatoria set plazas = plazas where id = id_convocatoria_examen;
    select sum(a.peso), coalesce(bool_or(a.area = new.area), false)
    into peso_total, area_valida
    from area_examen a where a.convocatoria_id = id_convocatoria_examen and a.cargo_id = id_cargo_examen;

    if (peso_total is null and new.area <> 'general')
       or (peso_total is not null and (peso_total <> 100 or not area_valida)) then
        raise exception 'El área de examen debe existir y su categoría debe sumar 100'
            using errcode = '23514';
    end if;
    if not exists (
        select 1 from asignacion_evaluador a join usuario u on u.id = a.evaluador_id
        where a.postulacion_id = new.postulacion_id and u.activo and u.rol = 'EVALUADOR'
          and u.id = new.evaluador_id
          and (upper(a.tipo) in ('EXAMEN','MERITOS_Y_EXAMEN')
               or (new.area <> 'general' and upper(a.tipo) <> 'MERITOS' and lower(a.tipo) = new.area))
    ) then
        raise exception 'El examen requiere un evaluador activo asignado'
            using errcode = '23514';
    end if;
    return new;
end
$$;

create or replace trigger examen_configuracion_trg
before insert or update of postulacion_id, area, evaluador_id on examen
for each row execute function inf513_validar_examen();

create or replace function inf513_validar_evaluador()
returns trigger language plpgsql as $$
begin
    if not exists (
        select 1 from asignacion_evaluador a join usuario u on u.id=a.evaluador_id
        where a.postulacion_id=new.postulacion_id and a.evaluador_id=new.evaluador_id
          and u.activo and u.rol='EVALUADOR'
          and (tg_table_name='evaluacion_requisito' or upper(a.tipo) in ('MERITOS','MERITOS_Y_EXAMEN'))
    ) then
        raise exception 'La evaluación requiere un evaluador activo con la asignación correspondiente'
            using errcode='23514';
    end if;
    return new;
end
$$;

create or replace trigger requisito_evaluador_trg
before insert or update of postulacion_id, evaluador_id on evaluacion_requisito
for each row execute function inf513_validar_evaluador();

create or replace trigger merito_evaluador_trg
before insert or update of postulacion_id, evaluador_id on merito
for each row execute function inf513_validar_evaluador();
