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


-- Se preparan las tablas anteriores para el esquema vigente.
alter table if exists examen add column if not exists area varchar(80) not null default 'general';
alter table if exists postulacion add column if not exists evaluacion_version bigint not null default 0;
alter table if exists asignacion_evaluador alter column tipo type varchar(80);
alter table if exists auditoria alter column entidad_id type varchar(160);
alter table if exists expediente
    add column if not exists nombre_archivo varchar(180),
    add column if not exists contenido bytea,
    add column if not exists sha256 char(64);
alter table if exists correo_control
    add column if not exists token_hash varchar(64),
    add column if not exists orden_json text,
    add column if not exists vence_en timestamptz,
    add column if not exists confirmacion_id varchar(255),
    add column if not exists usado boolean not null default false,
    add column if not exists resultado_exito boolean,
    add column if not exists resultado_asunto text,
    add column if not exists resultado_cuerpo text,
    add column if not exists ejecutado_en timestamptz;

do $$
begin
    if to_regclass(format('%I.correo_control', current_schema())) is not null
       and not exists (
          select 1 from pg_attribute where attrelid = to_regclass(format('%I.correo_control', current_schema()))
          and attname = 'admitido_en' and not attisdropped
       ) then
        alter table correo_control add column admitido_en timestamptz;
        update correo_control set admitido_en = creado_en where estado <> 'DIFERIDO';
    end if;
end
$$;

-- Cambia las referencias de evaluación antes de crear los disparadores nuevos.
do $$
declare
    tabla text;
    relacion regclass;
    faltantes bigint;
begin
    foreach tabla in array array['evaluacion_requisito','merito','examen'] loop
        relacion := to_regclass(format('%I.%I',current_schema(),tabla));
        if not exists (select 1 from pg_attribute where attrelid=relacion
                       and attname='evaluador_correo' and not attisdropped) then
            continue;
        end if;
        if exists (select 1 from usuario group by lower(correo) having count(*)>1) then
            raise exception 'Hay cuentas ambiguas por correo en usuario';
        end if;
        execute format('alter table %s add column evaluador_id bigint references usuario',relacion);
        execute format('update %s e set evaluador_id=u.id from usuario u where lower(u.correo)=lower(e.evaluador_correo)',relacion);
        execute format('select count(*) from %s where evaluador_id is null',relacion) into faltantes;
        if faltantes > 0 then
            raise exception '% contiene % evaluaciones sin usuario asociado. Revisá los correos históricos.',tabla,faltantes
                using errcode='23503';
        end if;
        if tabla='examen' then
            drop trigger if exists p1_examen_configuracion_trg on examen;
            drop trigger if exists examen_configuracion_trg on examen;
        end if;
        execute format('alter table %s alter column evaluador_id set not null, drop column evaluador_correo',relacion);
    end loop;
end
$$;

-- Renombra metadatos de las tablas del proyecto, incluidas secuencias y claves heredadas.
do $$
declare
    objeto record;
    tablas text[] := array[
        'adjunto_correo','area_examen','asignacion_evaluador','auditoria','buzon','cargo',
        'convocatoria','correo_control','desempate','evaluacion_requisito','examen',
        'expediente','merito','oferta','postulacion','usuario'
    ];
begin
    for objeto in
        select r.oid::regclass tabla,c.conname nombre
        from pg_constraint c join pg_class r on r.oid=c.conrelid
        where r.relnamespace=current_schema()::regnamespace and r.relname=any(tablas)
        and starts_with(c.conname,'p1_')
    loop
        execute format('alter table %s rename constraint %I to %I',
                       objeto.tabla,objeto.nombre,substring(objeto.nombre from 4));
    end loop;
    for objeto in
        select r.oid::regclass tabla,t.tgname nombre
        from pg_trigger t join pg_class r on r.oid=t.tgrelid
        where r.relnamespace=current_schema()::regnamespace and r.relname=any(tablas)
        and not t.tgisinternal and starts_with(t.tgname,'p1_')
    loop
        execute format('alter trigger %I on %s rename to %I',
                       objeto.nombre,objeto.tabla,substring(objeto.nombre from 4));
    end loop;
    for objeto in
        select i.oid::regclass oid,i.relname nombre
        from pg_index x join pg_class i on i.oid=x.indexrelid join pg_class r on r.oid=x.indrelid
        where r.relnamespace=current_schema()::regnamespace and r.relname=any(tablas)
        and i.relname ~ '^(p1_|uq_p1_|idx_p1_)'
    loop
        execute format('alter index %s rename to %I',objeto.oid,
                       regexp_replace(objeto.nombre,'(^|^(uq|idx)_)p1_','\1'));
    end loop;
    for objeto in
        select s.oid::regclass oid,s.relname nombre
        from pg_depend d join pg_class r on r.oid=d.refobjid join pg_class s on s.oid=d.objid
        where d.classid='pg_class'::regclass and d.refclassid='pg_class'::regclass and d.deptype in ('a','i')
        and r.relnamespace=current_schema()::regnamespace and r.relname=any(tablas)
        and s.relkind='S' and starts_with(s.relname,'p1_')
    loop
        execute format('alter sequence %s rename to %I',objeto.oid,substring(objeto.nombre from 4));
    end loop;
end
$$;

-- El índice único por lower(correo) se crea con el esquema vigente.
alter table if exists usuario drop constraint if exists usuario_correo_key;
do $$
begin
    if exists (select 1 from pg_index x join pg_class i on i.oid=x.indexrelid
               where i.relname='idx_usuario_correo'
                 and x.indrelid=to_regclass(format('%I.usuario',current_schema()))) then
        execute format('drop index %I.idx_usuario_correo',current_schema());
    end if;
end
$$;

-- Las versiones anteriores admitían menos estados de correo.
alter table if exists correo_control drop constraint if exists correo_control_estado_check;
alter table if exists correo_control add constraint correo_control_estado_check
    check(estado in('DIFERIDO','RECIBIDO','RESPUESTA_PENDIENTE','RESPONDIDO','INTERNO'));
