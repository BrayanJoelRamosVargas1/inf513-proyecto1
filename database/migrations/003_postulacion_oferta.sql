-- Un cupo global anterior se puede trasladar cuando solo hay un cargo.
lock table oferta, postulacion in share row exclusive mode;

insert into oferta(convocatoria_id,cargo_id,plazas)
select p.convocatoria_id, min(p.cargo_id), c.plazas
from postulacion p join convocatoria c on c.id=p.convocatoria_id
where not exists (select 1 from oferta o where o.convocatoria_id=p.convocatoria_id)
group by p.convocatoria_id,c.plazas
having count(distinct p.cargo_id)=1;

do $$
declare
    faltante record;
begin
    select p.convocatoria_id, p.cargo_id into faltante
    from postulacion p left join oferta o using(convocatoria_id,cargo_id)
    where o.cargo_id is null order by p.convocatoria_id, p.cargo_id limit 1;
    if found then
        raise exception 'Falta configurar la oferta histórica de la convocatoria % y el cargo %. Revisá el reparto antes de volver a migrar.',
            faltante.convocatoria_id, faltante.cargo_id using errcode='23503';
    end if;

    select o.convocatoria_id, o.cargo_id into faltante
    from oferta o join postulacion p using(convocatoria_id,cargo_id)
    where p.plaza_asignada
    group by o.convocatoria_id,o.cargo_id,o.plazas
    having count(*)>o.plazas
    order by o.convocatoria_id,o.cargo_id limit 1;
    if found then
        raise exception 'Hay más ganadores que plazas en la convocatoria %, cargo %. Revisá el cupo antes de volver a migrar.',
            faltante.convocatoria_id, faltante.cargo_id using errcode='23514';
    end if;

    if not exists (select 1 from pg_constraint
                   where conrelid='postulacion'::regclass and conname='postulacion_oferta_fk') then
        alter table postulacion add constraint postulacion_oferta_fk
            foreign key(convocatoria_id,cargo_id) references oferta(convocatoria_id,cargo_id);
        alter table postulacion
            drop constraint if exists postulacion_convocatoria_id_fkey,
            drop constraint if exists postulacion_cargo_id_fkey,
            drop constraint postulacion_plaza_ck,
            add constraint postulacion_plaza_ck check (plaza_asignada = (estado='GANADOR'));
        drop index if exists idx_postulacion_convocatoria;
    end if;
end
$$;
