package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

/** Configuración y evaluación por categoría. Usa exclusivamente la transacción del repositorio. */
final class ProcesoAuxiliaturas {
    static final Set<String> COMANDOS=Set.of("MODUSU","PERFIL","MODCON","OFERTA","LISOFE","REQUISITO","LISREQ","AREA","LISAREA","ASIAREA","NOTAREA","MER40","DOCPOST","LISDOC","VERDOC","RESEMP","REPFIN","REPDOC","REPAREA");
    static final Set<String> ESCRITURAS=Set.of("MODUSU","PERFIL","MODCON","OFERTA","REQUISITO","AREA","ASIAREA","NOTAREA","MER40","DOCPOST","RESEMP");
    private final Connection c;
    ProcesoAuxiliaturas(Connection c){this.c=c;}
    private PreparedStatement stmt(String sql,Object... args)throws SQLException{
        var s=c.prepareStatement(sql);s.setQueryTimeout(30);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;
    }
    long n(String sql,Object... args)throws SQLException{try(var s=stmt(sql,args);var r=s.executeQuery()){return r.next()?r.getLong(1):0;}}
    private int update(String sql,Object... args)throws SQLException{try(var s=stmt(sql,args)){return s.executeUpdate();}}
    private Resultado cambio(String sql,Object... args)throws SQLException{return update(sql,args)>0?Resultado.ok("Cambio registrado."):Resultado.error("No existe el registro o no se cumplen las condiciones de la operación.");}
    Resultado tabla(String sql,Object... args)throws SQLException{
        var b=new StringBuilder();try(var s=stmt(sql,args)){s.setMaxRows(201);try(var r=s.executeQuery()){
            var m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++)b.append(i>1?" | ":"").append(m.getColumnLabel(i));b.append('\n');int filas=0;
            while(r.next()){if(++filas>200||b.length()>45000){b.append("Resultado limitado a 200 filas y 45000 caracteres.\n");break;}for(int i=1;i<=m.getColumnCount();i++){String v=r.getString(i);b.append(i>1?" | ":"").append(v==null?"-":v.substring(0,Math.min(v.length(),1000)));}b.append('\n');}
            return Resultado.ok(filas==0?"Sin resultados.":b.toString());
        }}
    }
    void bloquearConvocatoria(long id)throws SQLException{try(var s=stmt("select id from p1_convocatoria where id=? for update",id);var r=s.executeQuery()){if(!r.next())throw new SQLException("Convocatoria inexistente","23503");}}
    long ofertaPost(long post)throws SQLException{return n("select o.id from p1_postulacion p join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id where p.id=?",post);}
    boolean configurada(long convocatoria)throws SQLException{return n("select count(*) from p1_oferta where convocatoria_id=?",convocatoria)>0;}
    boolean puedeLeer(String correo,long post)throws SQLException{return n("select count(*) from p1_usuario u where lower(u.correo)=lower(?) and u.activo and (u.rol in('DECANO','VICEDECANO') or (u.rol='POSTULANTE' and exists(select 1 from p1_postulacion p where p.id=? and p.postulante_id=u.id)) or (u.rol='EVALUADOR' and (exists(select 1 from p1_asignacion_evaluador a where a.postulacion_id=? and a.evaluador_id=u.id) or exists(select 1 from p1_asignacion_area a where a.postulacion_id=? and a.evaluador_id=u.id))))",correo,post,post,post)>0;}
    boolean sinGanadores(long oferta)throws SQLException{return n("select count(*) from p1_postulacion p join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id where o.id=? and p.plaza_asignada",oferta)==0;}
    boolean congelada(long oferta)throws SQLException{return n("select count(*) from p1_postulacion p join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id where o.id=?",oferta)>0;}
    private void bloquearOferta(long oferta)throws SQLException{
        long convocatoria=n("select convocatoria_id from p1_oferta where id=?",oferta);
        if(convocatoria>0)bloquearConvocatoria(convocatoria);
    }
    String validarPostulacion(String correo,long convocatoria,long cargo)throws SQLException{
        bloquearConvocatoria(convocatoria);
        if(!configurada(convocatoria))return null; // Legacy records remain usable; new configured calls enforce the full process.
        try(var s=stmt("select o.id,o.facultades,o.carreras,o.multiple,u.facultad,u.carrera,u.id from p1_oferta o join p1_usuario u on lower(u.correo)=lower(?) where o.convocatoria_id=? and o.cargo_id=?",correo,convocatoria,cargo);var r=s.executeQuery()){
            if(!r.next())return "El cargo no está ofertado en esta convocatoria.";
            try(var pesos=stmt("select coalesce(sum(porcentaje),0) from p1_area_examen where oferta_id=? and activo",r.getLong(1));var suma=pesos.executeQuery()){
                suma.next();if(suma.getBigDecimal(1).compareTo(new BigDecimal("100"))!=0)return "La oferta aún no tiene áreas activas que sumen 100 %.";
            }
            if(!admite(r.getString(2),r.getString(5))||!admite(r.getString(3),r.getString(6)))return "El perfil académico no cumple el alcance de la oferta. Solicite registrar facultad y carrera.";
            long previas=n("select count(*) from p1_postulacion where convocatoria_id=? and postulante_id=?",convocatoria,r.getLong(7));
            if(previas>0&&(!r.getBoolean(4)||n("select count(*) from p1_postulacion p join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id where p.convocatoria_id=? and p.postulante_id=? and not o.multiple",convocatoria,r.getLong(7))>0))return "La convocatoria no permite esta postulación múltiple.";
        }return null;
    }
    private static boolean admite(String lista,String valor){return lista.equals("*")||(valor!=null&&Arrays.stream(lista.split(";")).map(String::trim).anyMatch(v->v.equalsIgnoreCase(valor)));}
    String requisitosPendientes(long post)throws SQLException{
        long oferta=ofertaPost(post);if(oferta==0)return null;
        if(n("select count(*) from p1_requisito_config q where q.oferta_id=? and q.activo and q.obligatorio and (not exists(select 1 from p1_evaluacion_requisito e where e.postulacion_id=? and e.requisito=q.codigo and e.cumple and e.id=(select max(e2.id) from p1_evaluacion_requisito e2 where e2.postulacion_id=e.postulacion_id and e2.requisito=e.requisito)) or (q.documental and not exists(select 1 from p1_expediente x where x.postulacion_id=? and x.requisito_id=q.id and x.contenido is not null)))",oferta,post,post)>0)return "Faltan requisitos aprobados o documentos obligatorios almacenados.";
        return null;
    }
    boolean examenCompleto(long post)throws SQLException{
        long o=ofertaPost(post);if(o==0)return true;
        return n("select coalesce(sum(porcentaje),0) from p1_area_examen where oferta_id=? and activo",o)==100&&n("select count(*) from p1_area_examen a where a.oferta_id=? and a.activo and (not exists(select 1 from p1_asignacion_area x where x.postulacion_id=? and x.area_id=a.id) or exists(select 1 from p1_asignacion_area x join p1_usuario u on u.id=x.evaluador_id where x.postulacion_id=? and x.area_id=a.id and (not u.activo or not exists(select 1 from p1_nota_area n where n.postulacion_id=x.postulacion_id and n.evaluador_id=x.evaluador_id and n.area_id=x.area_id))))",o,post,post)==0;
    }
    void recalcular(long post)throws SQLException{
        long oferta=ofertaPost(post);if(oferta==0)return;
        update("update p1_postulacion p set puntaje_meritos=least(100,coalesce((select sum(m.puntaje) from p1_merito m where m.postulacion_id=p.id),0)),puntaje_examen=round(coalesce((select sum(a.porcentaje*(select avg(n.puntaje) from p1_nota_area n where n.postulacion_id=p.id and n.area_id=a.id)/100) from p1_area_examen a where a.oferta_id=? and a.activo),0),2) where p.id=?",oferta,post);
        update("update p1_postulacion set puntaje_final=round(puntaje_meritos*0.4+puntaje_examen*0.6,2) where id=?",post);
        update("delete from p1_resolucion_empate where postulacion_id in(select q.id from p1_postulacion q join p1_postulacion p on p.convocatoria_id=q.convocatoria_id and p.cargo_id=q.cargo_id where p.id=?)",post);
    }
    Resultado ejecutar(String correo,Orden orden,String mensajeId)throws Exception{
        var p=orden.parametros();long id=Long.parseLong(p.get(0));
        switch(orden.comando()){
            case "MODUSU": {
                if(n("select count(*) from p1_usuario where id=? and rol in('DECANO','VICEDECANO') and activo",id)>0&&(!Boolean.parseBoolean(p.get(6))||!Set.of("DECANO","VICEDECANO").contains(p.get(3)))&&n("select count(*) from p1_usuario where id<>? and rol in('DECANO','VICEDECANO') and activo",id)==0)return Resultado.error("No se puede retirar la última autoridad activa.");
                return cambio("update p1_usuario set nombres=?,apellidos=?,rol=?,telefono=?,correo=?,activo=? where id=?",p.get(1),p.get(2),p.get(3),p.get(4),p.get(5),Boolean.parseBoolean(p.get(6)),id);
            }
            case "PERFIL":return cambio("update p1_usuario set facultad=?,carrera=? where id=? and rol='POSTULANTE'",p.get(1),p.get(2),id);
            case "MODCON": {
                bloquearConvocatoria(id);
                int plazas=Integer.parseInt(p.get(5));
                if(plazas<n("select coalesce(sum(plazas),0) from p1_oferta where convocatoria_id=?",id)||plazas<n("select count(*) from p1_postulacion where convocatoria_id=? and plaza_asignada",id))return Resultado.error("El cupo no puede ser menor que las ofertas o plazas cubiertas.");
                return cambio("update p1_convocatoria set titulo=?,fecha_inicio=?::date,fecha_fin=?::date,estado=?,plazas=?,gestion=?,numero=? where id=?",p.get(1),p.get(2),p.get(3),p.get(4),plazas,p.get(6),p.get(7),id);
            }
            case "OFERTA": {
                bloquearConvocatoria(id);long cargo=Long.parseLong(p.get(1));long o=n("select id from p1_oferta where convocatoria_id=? and cargo_id=?",id,cargo);
                if(congelada(o)||n("select count(*) from p1_postulacion where convocatoria_id=?",id)>0)return Resultado.error("Configure las ofertas antes de recibir postulaciones; no se alteran reglas de participantes existentes.");
                if(Integer.parseInt(p.get(2))+n("select coalesce(sum(plazas),0) from p1_oferta where convocatoria_id=? and cargo_id<>?",id,cargo)>n("select plazas from p1_convocatoria where id=?",id))return Resultado.error("Las ofertas superan el cupo total autorizado.");
                return cambio("insert into p1_oferta(convocatoria_id,cargo_id,plazas,facultades,carreras,multiple) select ?,c.id,?,?,?,? from p1_cargo c where c.id=? and c.activo on conflict(convocatoria_id,cargo_id) do update set plazas=excluded.plazas,facultades=excluded.facultades,carreras=excluded.carreras,multiple=excluded.multiple",id,Integer.parseInt(p.get(2)),p.get(3),p.get(4),Boolean.parseBoolean(p.get(5)),cargo);
            }
            case "LISOFE":return tabla("select o.*,c.nombre,c.unidad from p1_oferta o join p1_cargo c on c.id=o.cargo_id where convocatoria_id=? order by o.id",id);
            case "REQUISITO": {
                bloquearOferta(id);
                if(congelada(id))return Resultado.error("Los requisitos se configuran antes de las postulaciones.");
                return cambio("insert into p1_requisito_config(oferta_id,codigo,descripcion,obligatorio,documental,activo) values(?,?,?,?,?,?) on conflict(oferta_id,codigo) do update set descripcion=excluded.descripcion,obligatorio=excluded.obligatorio,documental=excluded.documental,activo=excluded.activo",id,p.get(1),p.get(2),Boolean.parseBoolean(p.get(3)),Boolean.parseBoolean(p.get(4)),Boolean.parseBoolean(p.get(5)));
            }
            case "LISREQ":return tabla("select id,codigo,descripcion,obligatorio,documental,activo from p1_requisito_config where oferta_id=? order by id",id);
            case "AREA": {
                bloquearOferta(id);
                if(congelada(id))return Resultado.error("Las áreas y ponderaciones se configuran antes de las postulaciones.");
                boolean activa=Boolean.parseBoolean(p.get(3));BigDecimal porcentaje=new BigDecimal(p.get(2));
                try(var s=stmt("select id from p1_oferta where id=? for update",id);var r=s.executeQuery()){if(!r.next())return Resultado.error("Oferta inexistente.");}
                try(var s=stmt("select coalesce(sum(porcentaje),0) from p1_area_examen where oferta_id=? and activo and nombre<>?",id,p.get(1));var r=s.executeQuery()){r.next();if(activa&&r.getBigDecimal(1).add(porcentaje).compareTo(new BigDecimal("100"))>0)return Resultado.error("Las áreas activas no pueden superar 100 %.");}
                return cambio("insert into p1_area_examen(oferta_id,nombre,porcentaje,activo) values(?,?,?,?) on conflict(oferta_id,nombre) do update set porcentaje=excluded.porcentaje,activo=excluded.activo",id,p.get(1),porcentaje,activa);
            }
            case "LISAREA":return tabla("select id,nombre,porcentaje,activo from p1_area_examen where oferta_id=? order by id",id);
            case "ASIAREA": {
                long area=Long.parseLong(p.get(2));long o=ofertaPost(id);if(!sinGanadores(o))return Resultado.error("Retire las plazas de la categoría antes de modificar evaluadores.");
                return cambio("insert into p1_asignacion_area(postulacion_id,evaluador_id,area_id) select ?,u.id,a.id from p1_usuario u join p1_area_examen a on a.id=? where u.id=? and u.rol='EVALUADOR' and u.activo and a.activo and a.oferta_id=? on conflict do nothing",id,area,Long.parseLong(p.get(1)),o);
            }
            case "NOTAREA": {
                long o=ofertaPost(id);if(!sinGanadores(o))return Resultado.error("Retire las plazas de la categoría antes de modificar notas.");
                if(n("select count(*) from p1_postulacion where id=? and estado='HABILITADO'",id)==0)return Resultado.error("Se requiere una postulación habilitada.");
                if(n("select coalesce(sum(porcentaje),0) from p1_area_examen where oferta_id=? and activo",o)!=100)return Resultado.error("Las ponderaciones activas deben sumar exactamente 100 %.");
                var res=cambio("insert into p1_nota_area(postulacion_id,evaluador_id,area_id,puntaje,observacion) select x.postulacion_id,x.evaluador_id,x.area_id,?,? from p1_asignacion_area x join p1_usuario u on u.id=x.evaluador_id join p1_area_examen a on a.id=x.area_id where x.postulacion_id=? and x.area_id=? and lower(u.correo)=lower(?) and u.activo and a.activo on conflict(postulacion_id,evaluador_id,area_id) do update set puntaje=excluded.puntaje,observacion=excluded.observacion,actualizado_en=now()",new BigDecimal(p.get(2)),p.get(3),id,Long.parseLong(p.get(1)),correo);
                if(res.exito())recalcular(id);return res;
            }
            case "MER40": {
                long o=ofertaPost(id);if(!sinGanadores(o))return Resultado.error("Retire las plazas antes de modificar méritos.");
                if(n("select count(*) from p1_asignacion_evaluador a join p1_usuario u on u.id=a.evaluador_id where a.postulacion_id=? and lower(u.correo)=lower(?) and u.activo",id,correo)==0)return Resultado.error("El evaluador no está asignado a méritos.");
                BigDecimal normal=new BigDecimal(p.get(2)).multiply(new BigDecimal("2.5"));
                try(var s=stmt("select coalesce(sum(puntaje),0) from p1_merito where postulacion_id=? and not (criterio=? and lower(evaluador_correo)=lower(?))",id,p.get(1),correo);var r=s.executeQuery()){r.next();if(r.getBigDecimal(1).add(normal).compareTo(new BigDecimal("100"))>0)return Resultado.error("El total de méritos excedería 40 puntos.");}
                update("delete from p1_merito where postulacion_id=? and criterio=? and lower(evaluador_correo)=lower(?)",id,p.get(1),correo);
                update("insert into p1_merito(postulacion_id,criterio,puntaje,observacion,evaluador_correo) values(?,?,?,?,?)",id,p.get(1),normal,p.get(3),correo);
                return Resultado.ok("Mérito registrado sobre 40. Requiere recálculo transaccional.");
            }
            case "DOCPOST": {
                long o=ofertaPost(id);
                if(n("select count(*) from p1_postulacion p join p1_usuario u on u.id=p.postulante_id where p.id=? and lower(u.correo)=lower(?) and p.estado='REGISTRADA'",id,correo)==0)return Resultado.error("Solo el propietario puede adjuntar mientras la postulación está REGISTRADA.");
                long req=n("select id from p1_requisito_config where oferta_id=? and codigo=? and activo and documental",o,p.get(1));if(req==0)return Resultado.error("Requisito documental inexistente.");
                return cambio("insert into p1_expediente(postulacion_id,descripcion,ruta_archivo,tipo_mime,nombre_archivo,contenido,sha256,requisito_id) select ?,?,'postgres:documento','application/pdf',a.nombre,a.contenido,a.sha256,? from p1_adjunto_correo a where mensaje_id=? on conflict(postulacion_id,requisito_id) where requisito_id is not null do update set nombre_archivo=excluded.nombre_archivo,contenido=excluded.contenido,sha256=excluded.sha256,creado_en=now()",id,p.get(1),req,mensajeId);
            }
            case "LISDOC":return puedeLeer(correo,id)?tabla("select id,descripcion,requisito_id,nombre_archivo,octet_length(contenido) bytes,sha256 from p1_expediente where postulacion_id=? order by id",id):Resultado.error("No tiene acceso a este expediente.");
            case "VERDOC": {
                long post=n("select postulacion_id from p1_expediente where id=?",id);if(!puedeLeer(correo,post))return Resultado.error("No tiene acceso a este expediente.");
                try(var s=stmt("select nombre_archivo,contenido from p1_expediente where id=? and contenido is not null",id);var r=s.executeQuery()){
                    if(!r.next())return Resultado.error("El registro solo conserva una referencia externa, sin PDF almacenado.");
                    return new Resultado(true,"ÉXITO - Documento solicitado","Documento del expediente. Datos de acceso restringido.",r.getString(1),r.getBytes(2));
                }
            }
            case "RESEMP": {
                long o=ofertaPost(id);if(o==0||!sinGanadores(o))return Resultado.error("Se requiere una categoría configurada sin plazas ya asignadas.");
                return cambio("insert into p1_resolucion_empate(postulacion_id,autorizado,motivo,actor) select id,?,?,? from p1_postulacion where id=? and estado='HABILITADO' on conflict(postulacion_id) do update set autorizado=excluded.autorizado,motivo=excluded.motivo,actor=excluded.actor,fecha=now()",Boolean.parseBoolean(p.get(1)),p.get(2),correo,id);
            }
            case "REPFIN": {
                var resultado=tabla(RANKING+" select r.*,o.plazas from r left join p1_oferta o on o.convocatoria_id=r.convocatoria_id and o.cargo_id=r.cargo_id order by cargo_id,posicion,id",id);
                var plazas=tabla("select o.id oferta,c.nombre categoria,o.plazas,count(p.id) postulaciones,count(p.id) filter(where p.plaza_asignada) cubiertas,greatest(0,o.plazas-count(p.id) filter(where p.plaza_asignada)) vacantes from p1_oferta o join p1_cargo c on c.id=o.cargo_id left join p1_postulacion p on p.convocatoria_id=o.convocatoria_id and p.cargo_id=o.cargo_id where o.convocatoria_id=? group by o.id,c.nombre order by o.id",id);
                return Resultado.ok(resultado.cuerpo()+"\nPLAZAS POR CATEGORIA (incluye ofertas sin postulantes)\n"+plazas.cuerpo());
            }
            case "REPDOC":return tabla("select p.id postulacion,u.registro,p.cargo_id,p.estado,q.codigo,q.obligatorio,x.nombre_archivo,x.sha256,(select e.cumple from p1_evaluacion_requisito e where e.postulacion_id=p.id and e.requisito=q.codigo order by e.id desc limit 1) aprobado from p1_postulacion p join p1_usuario u on u.id=p.postulante_id left join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id left join p1_requisito_config q on q.oferta_id=o.id and q.activo left join p1_expediente x on x.postulacion_id=p.id and x.requisito_id=q.id where p.convocatoria_id=? order by p.id,q.id",id);
            case "REPAREA":return tabla("select p.id postulacion,p.cargo_id,a.nombre,a.porcentaje,u.correo evaluador,n.puntaje,n.observacion,round(n.puntaje*a.porcentaje*0.006/(select count(*) from p1_asignacion_area x2 where x2.postulacion_id=x.postulacion_id and x2.area_id=x.area_id),2) aporte_individual_60 from p1_postulacion p join p1_asignacion_area x on x.postulacion_id=p.id join p1_area_examen a on a.id=x.area_id join p1_usuario u on u.id=x.evaluador_id left join p1_nota_area n on n.postulacion_id=x.postulacion_id and n.area_id=x.area_id and n.evaluador_id=x.evaluador_id where p.convocatoria_id=? order by p.id,a.id,u.id",id);
            default:throw new IllegalArgumentException("Comando de categoría desconocido");
        }
    }
    static final String RANKING="with r as (select p.id,p.convocatoria_id,p.cargo_id,u.registro,u.nombres,u.apellidos,p.estado,round(p.puntaje_meritos*0.4,2) meritos_40,round(p.puntaje_examen*0.6,2) examen_60,p.puntaje_final,p.plaza_asignada,rank() over(partition by p.cargo_id order by p.puntaje_final desc) posicion,count(*) over(partition by p.cargo_id,p.puntaje_final) empatados from p1_postulacion p join p1_usuario u on u.id=p.postulante_id where p.convocatoria_id=? and p.estado in('HABILITADO','GANADOR'))";
    Resultado ranking(long conv)throws SQLException{return tabla(RANKING+" select * from r order by cargo_id,posicion,id",conv);}
    String validarPlaza(long post)throws SQLException{
        long o=ofertaPost(post);if(o==0)return null;
        if(!examenCompleto(post))return "Faltan notas de áreas o ponderaciones completas.";
        try(var s=stmt("select q.id from p1_postulacion q join p1_postulacion p on p.convocatoria_id=q.convocatoria_id and p.cargo_id=q.cargo_id where p.id=? and q.estado in('HABILITADO','GANADOR')",post);var r=s.executeQuery()){
            while(r.next())if(!examenCompleto(r.getLong(1)))return "La categoría todavía tiene candidatos habilitados con evaluación incompleta.";
        }
        try(var s=stmt("select p.convocatoria_id,p.cargo_id,p.puntaje_final,o.plazas from p1_postulacion p join p1_oferta o on o.convocatoria_id=p.convocatoria_id and o.cargo_id=p.cargo_id where p.id=?",post);var r=s.executeQuery()){
            if(!r.next())return "Postulación inexistente.";long conv=r.getLong(1),cargo=r.getLong(2);BigDecimal nota=r.getBigDecimal(3);int cupo=r.getInt(4);
            long superiores=n("select count(*) from p1_postulacion where convocatoria_id=? and cargo_id=? and estado in('HABILITADO','GANADOR') and puntaje_final>?",conv,cargo,nota);
            long iguales=n("select count(*) from p1_postulacion where convocatoria_id=? and cargo_id=? and estado in('HABILITADO','GANADOR') and puntaje_final=?",conv,cargo,nota);
            if(superiores>=cupo)return "La posición queda fuera de las plazas ofertadas.";
            if(n("select count(*) from p1_postulacion where convocatoria_id=? and cargo_id=? and plaza_asignada and id<>?",conv,cargo,post)>=cupo)return "No quedan plazas en esta categoría.";
            if(superiores+iguales>cupo){
                if(n("select count(*) from p1_resolucion_empate where postulacion_id=? and autorizado",post)==0)return "Empate en el corte: requiere resolución motivada mediante RESEMP.";
                if(n("select count(*) from p1_resolucion_empate e join p1_postulacion p on p.id=e.postulacion_id where p.convocatoria_id=? and p.cargo_id=? and p.puntaje_final=? and e.autorizado",conv,cargo,nota)>cupo-superiores)return "La resolución autoriza más candidatos que plazas en el empate.";
            }
        }return null;
    }
}
