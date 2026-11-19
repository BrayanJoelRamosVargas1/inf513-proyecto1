package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import bo.edu.uagrm.inf513.grupo09sa.modelo.AreaExamen;
import bo.edu.uagrm.inf513.grupo09sa.modelo.Desempate;
import bo.edu.uagrm.inf513.grupo09sa.modelo.Examen;
import bo.edu.uagrm.inf513.grupo09sa.modelo.Oferta;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Operaciones CU2/CU5/CU6/CU8 dentro de la transacción y bloqueo de convocatoria del repositorio. */
final class GestionCategorias {
    @FunctionalInterface interface Conexion { Connection abrir() throws SQLException; }
    private final Conexion conexiones;
    GestionCategorias(Conexion conexiones){this.conexiones=conexiones;}
    private static final String REQUISITOS="inf513_requisitos_vigentes(p.id)";
    private static final String COMPLETA="inf513_evaluacion_completa(p.id)";
    private static final String ELEGIBLE="p.estado in('HABILITADO','GANADOR') and "+REQUISITOS+" and "+COMPLETA;
    private PreparedStatement preparar(Connection c,String sql,Object... valores)throws SQLException {
        PreparedStatement s=c.prepareStatement(sql);
        try {
            s.setQueryTimeout(30);
            for(int i=0;i<valores.length;i++)s.setObject(i+1,valores[i]);
            return s;
        } catch(SQLException | RuntimeException fallo) {
            try { s.close(); } catch(SQLException cierre) { fallo.addSuppressed(cierre); }
            throw fallo;
        }
    }
    private boolean existe(String sql,Object... valores)throws SQLException {
        try(var c=conexiones.abrir();var s=preparar(c,sql,valores);var r=s.executeQuery()){return r.next();}
    }
    boolean tieneAreas(long post)throws SQLException {
        return existe("select 1 from postulacion p join area_examen a on a.convocatoria_id=p.convocatoria_id and a.cargo_id=p.cargo_id where p.id=?::bigint",post);
    }
    boolean completa(String post)throws SQLException {
        return existe("select 1 from postulacion p where p.id=?::bigint and "+COMPLETA,post);
    }
    private boolean cerrada(long conv)throws SQLException {
        return !existe("select 1 from convocatoria where id=?::bigint and estado in('BORRADOR','ABIERTA')",conv)
            ||existe("select 1 from postulacion where convocatoria_id=?::bigint and plaza_asignada",conv);
    }
    Resultado oferta(Oferta oferta) throws SQLException {
        if (cerrada(oferta.convocatoriaId()))
            return Resultado.error("La convocatoria no existe, está cerrada o ya tiene plazas asignadas.");
        if (!existe("select 1 from cargo where id=? and activo", oferta.cargoId()))
            return Resultado.error("El cargo no está activo.");
        try (var c = conexiones.abrir(); var s = preparar(c, """
                select c.plazas,coalesce((select sum(o.plazas) from oferta o
                    where o.convocatoria_id=c.id and o.cargo_id<>?),0)
                from convocatoria c where c.id=?
                """, oferta.cargoId(), oferta.convocatoriaId()); var r = s.executeQuery()) {
            if (!r.next() || r.getLong(2) + oferta.plazas() > r.getLong(1))
                return Resultado.error("La suma de plazas por cargo supera el total de la convocatoria.");
        }
        try (var c = conexiones.abrir(); var s = preparar(c, """
                insert into oferta(convocatoria_id,cargo_id,plazas) values(?,?,?)
                on conflict(convocatoria_id,cargo_id) do update set plazas=excluded.plazas
                """, oferta.convocatoriaId(), oferta.cargoId(), oferta.plazas())) {
            s.executeUpdate();
        }
        return Resultado.ok("Oferta y plazas por cargo configuradas.");
    }
    Resultado area(AreaExamen area) throws SQLException {
        if (cerrada(area.convocatoriaId()))
            return Resultado.error("La convocatoria no permite modificar ponderaciones.");
        if (!existe("select 1 from oferta where convocatoria_id=? and cargo_id=?",
                area.convocatoriaId(), area.cargoId()))
            return Resultado.error("Configure primero la oferta con ASIOFE.");
        if (existe("""
                select 1 from examen e join postulacion p on p.id=e.postulacion_id
                where p.convocatoria_id=? and p.cargo_id=?
                """, area.convocatoriaId(), area.cargoId()))
            return Resultado.error("No se cambian ponderaciones después de registrar exámenes.");
        try (var c = conexiones.abrir(); var s = preparar(c, """
                select coalesce(sum(peso),0) from area_examen where convocatoria_id=? and cargo_id=? and area<>?
                """, area.convocatoriaId(), area.cargoId(), area.area()); var r = s.executeQuery()) {
            r.next();
            if (r.getBigDecimal(1).add(area.peso()).compareTo(BigDecimal.valueOf(100)) > 0)
                return Resultado.error("Los porcentajes de las áreas no pueden superar 100.");
        }
        try (var c = conexiones.abrir(); var s = preparar(c, """
                insert into area_examen(convocatoria_id,cargo_id,area,peso) values(?,?,?,?)
                on conflict(convocatoria_id,cargo_id,area) do update set peso=excluded.peso
                """, area.convocatoriaId(), area.cargoId(), area.area(), area.peso())) {
            s.executeUpdate();
        }
        return Resultado.ok("Área configurada. Los porcentajes deben sumar 100 antes de registrar notas.");
    }
    boolean cargoOfertado(long conv,long cargo)throws SQLException {
        return existe("select 1 from oferta where convocatoria_id=? and cargo_id=? and plazas>0",conv,cargo);
    }
    Resultado registrarArea(Examen examen) throws SQLException {
        if(!existe("select 1 from postulacion p where p.id=?::bigint and p.estado in('HABILITADO','GANADOR') and "+REQUISITOS,examen.postulacionId()))return Resultado.error("Se requieren habilitación y requisitos vigentes para rendir el examen.");
        if(!existe("select 1 from postulacion p join area_examen a on a.convocatoria_id=p.convocatoria_id and a.cargo_id=p.cargo_id where p.id=?::bigint and a.area=? and (select sum(peso) from area_examen x where x.convocatoria_id=p.convocatoria_id and x.cargo_id=p.cargo_id)=100",examen.postulacionId(),examen.area()))return Resultado.error("El área debe estar configurada y los porcentajes deben sumar exactamente 100.");
        try(var c=conexiones.abrir();var s=preparar(c,"""
            insert into examen(postulacion_id,area,puntaje,observacion,evaluador_id)
            select ?,?,?,?,u.id from usuario u
            where lower(u.correo)=lower(?) and u.activo and u.rol='EVALUADOR'
              and exists(select 1 from asignacion_evaluador a
                where a.postulacion_id=? and a.evaluador_id=u.id
                  and (upper(a.tipo) in ('MERITOS_Y_EXAMEN','EXAMEN')
                       or (upper(a.tipo)<>'MERITOS' and lower(a.tipo)=?)))
            """, examen.postulacionId(), examen.area(), examen.puntaje(), examen.observacion(),
                examen.evaluadorCorreo(), examen.postulacionId(), examen.area())) {
            if(s.executeUpdate()!=1)return Resultado.error("El evaluador no está asignado a esta área del examen.");
        }
        return Resultado.ok("Nota de área registrada.");
    }
    private record Candidato(long id,BigDecimal puntaje,Integer prioridad){}
    private List<Candidato> candidatos(long conv,long cargo)throws SQLException {
        var salida=new ArrayList<Candidato>();
        String sql="select p.id,p.puntaje_final,d.prioridad from postulacion p left join desempate d on d.postulacion_id=p.id and d.evaluacion_version=p.evaluacion_version and d.puntaje=p.puntaje_final where p.convocatoria_id=?::bigint and p.cargo_id=?::bigint and "+ELEGIBLE+" order by p.puntaje_final desc,d.prioridad nulls last,p.id";
        try(var c=conexiones.abrir();var s=preparar(c,sql,Long.toString(conv),Long.toString(cargo))) {
            s.setMaxRows(5001);try(var r=s.executeQuery()){while(r.next())salida.add(new Candidato(r.getLong(1),r.getBigDecimal(2),(Integer)r.getObject(3)));}
        }
        if(salida.size()>5000)throw new SQLException("Demasiados candidatos para una categoría","54000");
        return salida;
    }
    Resultado resolver(String actor,List<String> p)throws SQLException {
        long convocatoria=Long.parseLong(p.get(0)), cargo=Long.parseLong(p.get(1));
        if (existe("select 1 from postulacion where convocatoria_id=? and cargo_id=? and plaza_asignada",
                convocatoria,cargo))
            return Resultado.error("Retire primero las plazas asignadas de este cargo antes de resolver un empate.");
        List<Long> ids=Arrays.stream(p.get(2).split(",")).map(Long::parseLong).toList();
        List<Candidato> todos=candidatos(convocatoria,cargo);
        Candidato primero=todos.stream().filter(c->c.id()==ids.get(0)).findFirst().orElse(null);
        if(primero==null)return Resultado.error("Las postulaciones deben tener evaluación completa y requisitos vigentes.");
        var empatados=todos.stream().filter(c->c.puntaje().compareTo(primero.puntaje())==0).map(Candidato::id).collect(java.util.stream.Collectors.toSet());
        if(empatados.size()<2||ids.size()!=empatados.size()||!new HashSet<>(ids).equals(empatados))return Resultado.error("Incluya exactamente todas las postulaciones del mismo empate, sin repetir ni mezclar categorías.");
        String sql="insert into desempate(postulacion_id,evaluacion_version,puntaje,prioridad,justificacion,actor) select id,evaluacion_version,puntaje_final,?::int,?,? from postulacion where id=?::bigint on conflict(postulacion_id) do update set evaluacion_version=excluded.evaluacion_version,puntaje=excluded.puntaje,prioridad=excluded.prioridad,justificacion=excluded.justificacion,actor=excluded.actor,fecha=now()";
        try (var c = conexiones.abrir()) {
            for (int i = 0; i < ids.size(); i++) {
                Desempate desempate = new Desempate(ids.get(i), i + 1, p.get(3), actor);
                try (var s = preparar(c, sql, desempate.prioridad(), desempate.justificacion(),
                        desempate.actor(), desempate.postulacionId())) {
                    s.executeUpdate();
                }
            }
        }
        return Resultado.ok("Empate resuelto por la autoridad con justificación y auditoría. Una modificación de notas invalida la resolución correspondiente.");
    }
    Resultado asignar(List<String> p)throws SQLException {
        long post=Long.parseLong(p.get(0));boolean asignar=Boolean.parseBoolean(p.get(1));
        long conv,cargo;int plazas;
        try(var c=conexiones.abrir();var s=preparar(c,"""
                select p.convocatoria_id,p.cargo_id,o.plazas from postulacion p
                join convocatoria c on c.id=p.convocatoria_id
                join oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id
                where p.id=? for update of c
                """,post);var r=s.executeQuery()) {
            if(!r.next())return Resultado.error("La postulación no existe.");conv=r.getLong(1);cargo=r.getLong(2);plazas=r.getInt(3);
        }
        if(asignar) {
            if(plazas<=0)return Resultado.error("No quedan plazas disponibles en este cargo.");
            try(var c=conexiones.abrir();var s=preparar(c,"select count(*) from postulacion where convocatoria_id=?::bigint and cargo_id=?::bigint and plaza_asignada and id<>?::bigint",Long.toString(conv),Long.toString(cargo),p.get(0));var r=s.executeQuery()) {
                r.next();if(r.getInt(1)>=plazas)return Resultado.error("No quedan plazas disponibles en este cargo.");
            }
            List<Candidato> lista=candidatos(conv,cargo);
            if(lista.stream().noneMatch(x->x.id()==post))
                return Resultado.error("Se requieren requisitos vigentes, méritos y examen completo antes de asignar una plaza.");
            int corte=Math.min(plazas,lista.size());
            BigDecimal notaCorte=lista.get(corte-1).puntaje();
            // Antes del primer ganador se resuelve cualquier empate que cruce el corte.
            if(lista.size()>corte&&lista.get(corte).puntaje().compareTo(notaCorte)==0) {
                var empate=lista.stream().filter(x->x.puntaje().compareTo(notaCorte)==0).toList();
                var prioridades=new HashSet<Integer>();
                if(empate.stream().anyMatch(x->x.prioridad()==null||!prioridades.add(x.prioridad())))return Resultado.error("Empate pendiente que afecta una plaza. La autoridad debe resolverlo con DESEMP y justificación.");
            }
            if(lista.subList(0,corte).stream().noneMatch(x->x.id()==post))return Resultado.error("La postulación no está dentro de las plazas disponibles según el ranking de su categoría.");
        }
        try(var c=conexiones.abrir();var s=preparar(c,"update postulacion set plaza_asignada=?::boolean,estado=case when ?::boolean then 'GANADOR' else case when estado='GANADOR' then 'HABILITADO' else estado end end where id=?::bigint and (not ?::boolean or estado in('HABILITADO','GANADOR'))",p.get(1),p.get(1),p.get(0),p.get(1))) {
            return s.executeUpdate()==1?Resultado.ok("Asignación de plaza actualizada por categoría. Filas afectadas: 1"):Resultado.error("La postulación debe estar habilitada.");
        }
    }
    String sqlRanking() {
        return """
            with elegibles as (
                select p.cargo_id,p.id,p.postulante_id,p.puntaje_final,d.prioridad
                from postulacion p left join desempate d on d.postulacion_id=p.id
                    and d.evaluacion_version=p.evaluacion_version and d.puntaje=p.puntaje_final
                where p.convocatoria_id=?::bigint and %s
            ), grupos as (
                select cargo_id,puntaje_final,count(*) cantidad,count(distinct prioridad)=count(*) resuelto
                from elegibles group by cargo_id,puntaje_final
            ), posiciones as (
                select e.cargo_id,e.id,e.postulante_id,e.puntaje_final,
                       case when g.cantidad>1 and g.resuelto then e.prioridad end prioridad,
                       case when g.cantidad=1 then 'SIN_EMPATE'
                            when g.resuelto then 'RESOLUCION_REGISTRADA'
                            else 'EMPATE_PENDIENTE' end empate
                from elegibles e join grupos g on g.cargo_id=e.cargo_id
                    and g.puntaje_final is not distinct from e.puntaje_final
            )
            select p.cargo_id,ca.nombre cargo,p.id postulacion,u.registro,u.nombres,u.apellidos,p.puntaje_final,
                   rank() over(partition by p.cargo_id order by p.puntaje_final desc,p.prioridad nulls last) posicion,
                   p.empate,p.prioridad
            from posiciones p join usuario u on u.id=p.postulante_id join cargo ca on ca.id=p.cargo_id
            order by p.cargo_id,posicion,p.id
            """.formatted(ELEGIBLE);
    }
    String sqlConfiguracion(){return "select o.cargo_id,c.nombre,o.plazas,a.area,a.peso,sum(a.peso) over(partition by o.cargo_id) porcentaje_total from oferta o join cargo c on c.id=o.cargo_id left join area_examen a on a.convocatoria_id=o.convocatoria_id and a.cargo_id=o.cargo_id where o.convocatoria_id=?::bigint order by o.cargo_id,a.area";}
    private static final String CUPOS_DEL_REPORTE = """
        with convocatoria as (
            select id from convocatoria where id=?::bigint
        ), disponibilidad as (
            select o.cargo_id, o.plazas, count(p.id) postulaciones,
                   count(p.id) filter (where p.plaza_asignada) cubiertas,
                   o.plazas-count(p.id) filter (where p.plaza_asignada) vacantes
            from oferta o join convocatoria c on c.id=o.convocatoria_id
            left join postulacion p on p.convocatoria_id=o.convocatoria_id and p.cargo_id=o.cargo_id
            group by o.cargo_id, o.plazas
        )
        """;

    String sqlConsolidado() {
        return CUPOS_DEL_REPORTE + """
            select p.cargo_id, c.nombre cargo, p.id postulacion, u.registro, p.estado,
                   (select count(*) from expediente e where e.postulacion_id=p.id) documentos,
                   p.puntaje_meritos, round(p.puntaje_meritos*0.4,2) aporte_meritos_40,
                   p.puntaje_examen, round(p.puntaje_examen*0.6,2) aporte_examen_60,
                   p.puntaje_final, p.plaza_asignada,
                   d.plazas, d.vacantes
            from postulacion p join convocatoria co on co.id=p.convocatoria_id
            join usuario u on u.id=p.postulante_id join cargo c on c.id=p.cargo_id
            join disponibilidad d on d.cargo_id=p.cargo_id
            order by p.cargo_id,p.puntaje_final desc,p.id
            """;
    }
    String sqlNotas(){return "select p.cargo_id,p.id postulacion,e.area,a.peso,avg(e.puntaje) promedio_area from postulacion p join lateral(select distinct on(area,evaluador_id) area,puntaje from examen where postulacion_id=p.id order by area,evaluador_id,id desc)e on true left join area_examen a on a.convocatoria_id=p.convocatoria_id and a.cargo_id=p.cargo_id and a.area=e.area where p.convocatoria_id=?::bigint group by p.cargo_id,p.id,e.area,a.peso order by p.cargo_id,p.id,e.area";}
    String sqlVacantes() {
        return CUPOS_DEL_REPORTE + """
            select d.cargo_id, c.nombre,
                   d.plazas, d.postulaciones, d.cubiertas, d.vacantes
            from disponibilidad d join cargo c on c.id=d.cargo_id
            order by d.cargo_id
            """;
    }
}
