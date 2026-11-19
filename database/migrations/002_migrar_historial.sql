-- Conserva cola, auditoría y archivos de instalaciones anteriores.
do $$
begin
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


update expediente set ruta_archivo='postgres:expediente/'||substring(ruta_archivo from length('postgres:p1_expediente/')+1)
where contenido is not null and starts_with(ruta_archivo,'postgres:p1_expediente/');

-- Completa las restricciones de instalaciones antiguas.
do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='convocatoria'::regclass and conname='convocatoria_fechas_ck') then
        alter table convocatoria add constraint convocatoria_fechas_ck check (fecha_fin>=fecha_inicio);
    end if;
    alter table convocatoria validate constraint convocatoria_fechas_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='convocatoria'::regclass and conname='convocatoria_estado_ck') then
        alter table convocatoria add constraint convocatoria_estado_ck check (estado in('BORRADOR','ABIERTA','CERRADA','CANCELADA','FINALIZADA'));
    end if;
    alter table convocatoria validate constraint convocatoria_estado_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='postulacion'::regclass and conname='postulacion_puntajes_ck') then
        alter table postulacion add constraint postulacion_puntajes_ck check (puntaje_meritos between 0 and 100 and puntaje_examen between 0 and 100 and puntaje_final between 0 and 100);
    end if;
    alter table postulacion validate constraint postulacion_puntajes_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='postulacion'::regclass and conname='postulacion_estado_ck') then
        alter table postulacion add constraint postulacion_estado_ck check (estado in('REGISTRADA','HABILITADO','INHABILITADO','GANADOR'));
    end if;
    alter table postulacion validate constraint postulacion_estado_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='postulacion'::regclass and conname='postulacion_plaza_ck') then
        alter table postulacion add constraint postulacion_plaza_ck check (not plaza_asignada or estado='GANADOR');
    end if;
    alter table postulacion validate constraint postulacion_plaza_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='merito'::regclass and conname='merito_puntaje_ck') then
        alter table merito add constraint merito_puntaje_ck check (puntaje between 0 and 100);
    end if;
    alter table merito validate constraint merito_puntaje_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='examen'::regclass and conname='examen_puntaje_ck') then
        alter table examen add constraint examen_puntaje_ck check (puntaje between 0 and 100);
    end if;
    alter table examen validate constraint examen_puntaje_ck;
end
$$;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid='auditoria'::regclass and conname='auditoria_referencia_ck') then
        alter table auditoria add constraint auditoria_referencia_ck check (
        (persona_ci is null or (entidad = 'INSPER' and persona_ci is not distinct from entidad_id))
        and (postulacion_id is null or (entidad <> 'INSPER'
             and (entidad not in ('EVAREQ','HABPOS','REGMER','REGEXA','REGAREA','ASIEVA','ASIPLA')
                  or postulacion_id::text is not distinct from ltrim(ltrim(entidad_id,'+'),'0'))))
    );
    end if;
    alter table auditoria validate constraint auditoria_referencia_ck;
end
$$;
