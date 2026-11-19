alter table desempate add column if not exists grupo_hash char(64);

-- Conserva solo las decisiones anteriores que abarcan todo el empate vigente.
with pendientes as (
    select distinct p.convocatoria_id
    from desempate d join postulacion p on p.id=d.postulacion_id
    where d.grupo_hash is null
), anteriores as (
    select c.id,c.grupo_hash,c.cantidad,d.prioridad
    from pendientes p cross join lateral inf513_candidatos(p.convocatoria_id,null) c
    join desempate d on d.postulacion_id=c.id
      and d.evaluacion_version=c.evaluacion_version and d.puntaje=c.puntaje_final
    where d.grupo_hash is null and c.cantidad>1
), completas as (
    select grupo_hash from anteriores group by grupo_hash,cantidad
    having count(distinct prioridad)=cantidad and min(prioridad)=1 and max(prioridad)=cantidad
)
update desempate d set grupo_hash=a.grupo_hash
from anteriores a join completas using(grupo_hash)
where d.postulacion_id=a.id and d.grupo_hash is null;
