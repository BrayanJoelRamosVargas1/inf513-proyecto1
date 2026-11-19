-- Renombra el conjunto anterior dentro de la transacción de instalación.
-- Una base compartida puede tener tablas homónimas ajenas al proyecto.
do $$
declare
    nombres text[] := array[
        'adjunto_correo','area_examen','asignacion_evaluador','auditoria','buzon','cargo',
        'convocatoria','correo_control','desempate','evaluacion_requisito','examen',
        'expediente','merito','oferta','postulacion','usuario'
    ];
    nombre text;
    nombres_nuevos integer := 0;
    tiene_prefijo boolean := false;
begin
    foreach nombre in array nombres loop
        if to_regclass(format('%I.%I',current_schema(),nombre)) is not null then
            nombres_nuevos := nombres_nuevos + 1;
        end if;
        if to_regclass(format('%I.%I',current_schema(),'p1_'||nombre)) is not null then
            tiene_prefijo := true;
        end if;
    end loop;

    if nombres_nuevos > 0 and (
        tiene_prefijo or nombres_nuevos <> cardinality(nombres)
        or not exists (
            select 1 from pg_attribute
            where attrelid=to_regclass(format('%I.correo_control', current_schema()))
              and attname='buzon_protocolo' and not attisdropped
        )
    ) then
        raise exception 'Hay tablas con nombres de destino sin identificar como el esquema completo del proyecto. Revisá las colisiones antes de migrar.';
    end if;

    if tiene_prefijo then
        foreach nombre in array nombres loop
            if to_regclass(format('%I.%I',current_schema(),'p1_'||nombre)) is not null then
                execute format('alter table %I.%I rename to %I',current_schema(),'p1_'||nombre,nombre);
            end if;
        end loop;
    end if;
end
$$;

-- Literal example from section 3 of the official assignment. This is NOT usuario.
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
    correo varchar(160) unique not null,
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
    creado_en timestamptz not null default now()
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
    unique(convocatoria_id,postulante_id,cargo_id)
);
create table if not exists expediente (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    descripcion varchar(200) not null,
    ruta_archivo text not null,
    tipo_mime varchar(120),
    creado_en timestamptz not null default now()
);
create table if not exists evaluacion_requisito (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    requisito varchar(200) not null,
    cumple boolean not null,
    observacion text,
    evaluador_correo varchar(160) not null,
    fecha timestamptz not null default now()
);
create table if not exists merito (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    criterio varchar(200) not null,
    puntaje numeric(7,2) not null,
    observacion text,
    evaluador_correo varchar(160) not null,
    fecha timestamptz not null default now()
);
create table if not exists examen (
    id bigserial primary key,
    postulacion_id bigint not null references postulacion,
    puntaje numeric(7,2) not null,
    evaluador_correo varchar(160) not null,
    observacion text,
    fecha timestamptz not null default now()
);
alter table examen add column if not exists area varchar(80) not null default 'general';
alter table postulacion add column if not exists evaluacion_version bigint not null default 0;
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
alter table asignacion_evaluador alter column tipo type varchar(80);
create table if not exists auditoria (
    id bigserial primary key,
    fecha timestamptz not null default now(),
    actor varchar(160) not null,
    accion varchar(80) not null,
    entidad varchar(80) not null,
    entidad_id varchar(160),
    detalle text
);
alter table auditoria alter column entidad_id type varchar(160);
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
    actualizado_en timestamptz not null default now()
);
alter table correo_control drop constraint if exists p1_correo_control_estado_check;
alter table correo_control drop constraint if exists correo_control_estado_check;
alter table correo_control add constraint p1_correo_control_estado_check check(estado in('DIFERIDO','RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO','INTERNO'));
alter table correo_control
    add column if not exists token_hash varchar(64),
    add column if not exists orden_json text,
    add column if not exists vence_en timestamptz,
    add column if not exists confirmacion_id varchar(255),
    add column if not exists usado boolean not null default false,
    add column if not exists resultado_exito boolean,
    add column if not exists resultado_asunto text,
    add column if not exists resultado_cuerpo text,
    add column if not exists ejecutado_en timestamptz;
create unique index if not exists uq_correo_confirmacion on correo_control(token_hash) where token_hash is not null;
-- Los mensajes históricos quedan RESPONDIDO sin consumir el cupo de recepción.
do $$
begin
    if not exists (
        select 1 from pg_attribute where attrelid='correo_control'::regclass
        and attname='admitido_en' and not attisdropped
    ) then
        alter table correo_control add column admitido_en timestamptz;
        update correo_control set admitido_en=creado_en where estado<>'DIFERIDO';
    end if;

    if to_regclass(format('%I.p1_correo_procesado', current_schema())) is not null then
        execute format(
            'insert into correo_control(mensaje_id,remitente,asunto,estado,exito,creado_en,actualizado_en,siguiente_intento)
             select mensaje_id,remitente,asunto,%L,exito,procesado_en,procesado_en,procesado_en from %I.p1_correo_procesado
             on conflict(mensaje_id) do nothing',
            'RESPONDIDO', current_schema()
        );
    end if;
end
$$;
create index if not exists idx_correo_admitido on correo_control(admitido_en,lower(remitente));
-- Se conservan las tablas anteriores como respaldo si ya existían.
do $$
begin
    if to_regclass(format('%I.p1_confirmacion', current_schema())) is not null then
        insert into correo_control(mensaje_id,remitente,estado,token_hash,orden_json,vence_en,confirmacion_id,usado)
        select mensaje_id,remitente,'INTERNO',token_hash,orden_json,vence_en,confirmacion_id,usado
        from p1_confirmacion
        on conflict(mensaje_id) do update set
            token_hash=excluded.token_hash, orden_json=excluded.orden_json, vence_en=excluded.vence_en,
            confirmacion_id=excluded.confirmacion_id, usado=excluded.usado
        where correo_control.token_hash is null;
    end if;

    if to_regclass(format('%I.p1_resultado_orden', current_schema())) is not null then
        -- Una confirmación antigua guardaba además un resultado con una clave derivada del correo original.
        update correo_control c
        set resultado_exito=r.exito, resultado_asunto=r.respuesta_asunto,
            resultado_cuerpo=r.respuesta_cuerpo, ejecutado_en=r.ejecutado_en
        from p1_resultado_orden r
        where c.usado and c.resultado_exito is null
          and r.mensaje_id='confirmacion:'||encode(sha256(convert_to(c.mensaje_id,'UTF8')),'hex');

        insert into correo_control(mensaje_id,remitente,estado,resultado_exito,resultado_asunto,resultado_cuerpo,ejecutado_en)
        select r.mensaje_id,'local', 'INTERNO',r.exito,r.respuesta_asunto,r.respuesta_cuerpo,r.ejecutado_en
        from p1_resultado_orden r
        where not exists (
            select 1 from correo_control c where c.usado
              and r.mensaje_id='confirmacion:'||encode(sha256(convert_to(c.mensaje_id,'UTF8')),'hex')
        )
        on conflict(mensaje_id) do update set resultado_exito=excluded.resultado_exito,
            resultado_asunto=excluded.resultado_asunto, resultado_cuerpo=excluded.resultado_cuerpo,
            ejecutado_en=excluded.ejecutado_en
        where correo_control.resultado_exito is null;
    end if;
end
$$;
create table if not exists buzon (
    protocolo varchar(80) primary key,
    inicializado boolean not null default false,
    actualizado_en timestamptz not null default now()
);
insert into buzon(protocolo) values ('pop3'),('imap'),('imaps'),('roundcube')
on conflict(protocolo) do nothing;

-- Conserva el estado anterior del buzón para que una actualización no inicialice de nuevo el historial.
do $$
begin
    if to_regclass(format('%I.p1_servicio_estado', current_schema())) is not null then
        insert into buzon(protocolo,inicializado,actualizado_en)
        select substring(clave from length('bandeja_inicializada_')+1),true,actualizado_en
        from p1_servicio_estado
        where starts_with(clave,'bandeja_inicializada_') and valor='true'
          and length(clave)>length('bandeja_inicializada_')
        on conflict(protocolo) do update set inicializado=true,actualizado_en=excluded.actualizado_en
        where not buzon.inicializado;
    end if;
end
$$;

alter table correo_control add column if not exists buzon_protocolo varchar(80) references buzon(protocolo);
update correo_control set buzon_protocolo='roundcube'
where buzon_protocolo is null and starts_with(mensaje_id,'roundcube:INBOX:');

alter table auditoria
    add column if not exists mensaje_id varchar(255) references correo_control(mensaje_id) on delete set null,
    add column if not exists postulacion_id bigint references postulacion(id) on delete set null,
    add column if not exists persona_ci varchar(20) references persona(ci) on delete set null;
update auditoria a set postulacion_id=p.id
from postulacion p
where a.postulacion_id is null and a.entidad in ('EVAREQ','HABPOS','REGMER','REGEXA','REGAREA','ASIEVA','ASIPLA')
  and p.id::text=ltrim(ltrim(a.entidad_id,'+'),'0');
update auditoria a set persona_ci=p.ci
from persona p
where a.persona_ci is null and a.entidad='INSPER' and p.ci=a.entidad_id;

-- Non-destructive migration for actual PDF upload, separate from legacy file references.
alter table expediente add column if not exists nombre_archivo varchar(180);
alter table expediente add column if not exists contenido bytea;
alter table expediente add column if not exists sha256 char(64);
update expediente set ruta_archivo='postgres:expediente/'||substring(ruta_archivo from length('postgres:p1_expediente/')+1)
where contenido is not null and starts_with(ruta_archivo,'postgres:p1_expediente/');
create table if not exists adjunto_correo (
    mensaje_id varchar(255) primary key references correo_control(mensaje_id),
    nombre varchar(180) not null,
    contenido bytea not null check(octet_length(contenido) between 5 and 2097152),
    sha256 char(64) not null,
    creado_en timestamptz not null default now()
);
drop index if exists idx_usuario_correo;
create unique index if not exists uq_p1_usuario_correo_ci on usuario(lower(correo));
create index if not exists idx_postulacion_convocatoria on postulacion(convocatoria_id);
create index if not exists idx_auditoria_entidad on auditoria(entidad,entidad_id,fecha desc);
create index if not exists idx_correo_control_pendiente on correo_control(estado,siguiente_intento);
create index if not exists idx_correo_control_remitente_fecha on correo_control(lower(remitente),creado_en desc);
create index if not exists idx_correo_control_fecha on correo_control(creado_en desc);
alter table convocatoria drop constraint if exists p1_convocatoria_fechas_ck;
alter table convocatoria add constraint p1_convocatoria_fechas_ck check(fecha_fin>=fecha_inicio) not valid;
alter table merito drop constraint if exists p1_merito_puntaje_ck;
alter table merito add constraint p1_merito_puntaje_ck check(puntaje between 0 and 100) not valid;
alter table examen drop constraint if exists p1_examen_puntaje_ck;
alter table examen add constraint p1_examen_puntaje_ck check(puntaje between 0 and 100) not valid;
alter table postulacion drop constraint if exists p1_postulacion_puntajes_ck;
alter table postulacion add constraint p1_postulacion_puntajes_ck check(puntaje_meritos between 0 and 100 and puntaje_examen between 0 and 100 and puntaje_final between 0 and 100) not valid;
alter table convocatoria validate constraint p1_convocatoria_fechas_ck;
alter table merito validate constraint p1_merito_puntaje_ck;
alter table examen validate constraint p1_examen_puntaje_ck;
alter table postulacion validate constraint p1_postulacion_puntajes_ck;
alter table convocatoria drop constraint if exists p1_convocatoria_estado_ck;
alter table convocatoria add constraint p1_convocatoria_estado_ck check(estado in('BORRADOR','ABIERTA','CERRADA','CANCELADA','FINALIZADA')) not valid;
alter table postulacion drop constraint if exists p1_postulacion_estado_ck;
alter table postulacion add constraint p1_postulacion_estado_ck check(estado in('REGISTRADA','HABILITADO','INHABILITADO','GANADOR')) not valid;
alter table postulacion drop constraint if exists p1_postulacion_plaza_ck;
alter table postulacion add constraint p1_postulacion_plaza_ck check(not plaza_asignada or estado='GANADOR') not valid;
alter table convocatoria validate constraint p1_convocatoria_estado_ck;
alter table postulacion validate constraint p1_postulacion_estado_ck;
alter table postulacion validate constraint p1_postulacion_plaza_ck;

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

drop trigger if exists p1_oferta_cupo_trg on oferta;
create trigger p1_oferta_cupo_trg
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

drop trigger if exists p1_convocatoria_cupo_trg on convocatoria;
create trigger p1_convocatoria_cupo_trg
before update of plazas on convocatoria
for each row when (old.plazas is distinct from new.plazas)
execute function inf513_validar_cupo_convocatoria();

create or replace function inf513_validar_cargo_postulacion()
returns trigger language plpgsql as $$
begin
    perform 1 from convocatoria where id = new.convocatoria_id for update;
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

drop trigger if exists p1_postulacion_oferta_trg on postulacion;
create trigger p1_postulacion_oferta_trg
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

drop trigger if exists p1_area_total_trg on area_examen;
create trigger p1_area_total_trg
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

drop trigger if exists p1_examen_configuracion_trg on examen;
create trigger p1_examen_configuracion_trg
before insert or update of postulacion_id, area, evaluador_correo on examen
for each row execute function inf513_validar_examen();
