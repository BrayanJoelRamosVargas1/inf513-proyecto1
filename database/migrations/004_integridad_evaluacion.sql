-- Evita asociar nombres de área a los tipos generales de asignación.
do $$
begin
    if not exists (select 1 from pg_constraint
                   where conrelid='area_examen'::regclass and conname='area_nombre_reservado_ck') then
        alter table area_examen add constraint area_nombre_reservado_ck
            check (area not in ('general','meritos','examen','meritos_y_examen'));
    end if;
end
$$;
alter table persona drop constraint if exists persona_correo_key;

-- Las copias antiguas pueden contener un PDF cuyo hash todavía no se guardaba.
update expediente set sha256=encode(pg_catalog.sha256(contenido),'hex')
where contenido is not null and sha256 is null;

do $$
begin
    if not exists (select 1 from pg_constraint
                   where conrelid='expediente'::regclass and conname='expediente_contenido_ck') then
        alter table expediente add constraint expediente_contenido_ck check (contenido is null or (
            octet_length(contenido) between 5 and 2097152
            and sha256 is not distinct from encode(pg_catalog.sha256(contenido),'hex')
        ));
    end if;
    if not exists (select 1 from pg_constraint
                   where conrelid='adjunto_correo'::regclass and conname='adjunto_correo_sha256_ck') then
        alter table adjunto_correo add constraint adjunto_correo_sha256_ck
            check (sha256=encode(pg_catalog.sha256(contenido),'hex'));
    end if;
    if not exists (select 1 from pg_constraint
                   where conrelid='correo_control'::regclass and conname='correo_intentos_ck') then
        alter table correo_control add constraint correo_intentos_ck check (intentos>=0);
    end if;
end
$$;

-- Conserva adjudicaciones si aparecen diferencias que podrían cambiar el orden de mérito.
do $$
declare
    postulacion_inconsistente bigint;
begin
    select p.id into postulacion_inconsistente
    from postulacion p cross join lateral inf513_calcular_puntajes(p.id) n
    where exists (select 1 from postulacion g where g.convocatoria_id=p.convocatoria_id and g.plaza_asignada)
      and (p.puntaje_meritos,p.puntaje_examen,p.puntaje_final) is distinct from (n.meritos,n.examen,n.final)
    order by p.id limit 1;
    if found then
        raise exception 'El puntaje de la postulación % difiere del detalle y su convocatoria tiene plazas asignadas',
            postulacion_inconsistente using errcode='23514';
    end if;
end
$$;

with diferencias as (
    select p.id from postulacion p cross join lateral inf513_calcular_puntajes(p.id) n
    where (p.puntaje_meritos,p.puntaje_examen,p.puntaje_final) is distinct from (n.meritos,n.examen,n.final)
)
update postulacion p set evaluacion_version=evaluacion_version+1
from diferencias d where p.id=d.id;

alter table postulacion
    alter column puntaje_meritos set not null,
    alter column puntaje_examen set not null,
    alter column puntaje_final set not null;
