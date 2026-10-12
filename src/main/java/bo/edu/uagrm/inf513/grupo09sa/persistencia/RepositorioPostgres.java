package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.Orden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Proxy;

public final class RepositorioPostgres implements RepositorioProyecto {
    private final DataSource ds;
    private final ThreadLocal<Connection> transaccion=new ThreadLocal<>();
    public RepositorioPostgres(DataSource ds){this.ds=ds;}
    public record CorreoControl(String id,String remitente,String asunto,String estado,String respuestaAsunto,String respuestaCuerpo,Boolean exito,int intentos){}

    /** IDs conocidos, incluidos los pendientes. Evita descargar y ejecutar dos veces el mismo UIDL. */
    public Set<String> correosConocidos()throws SQLException{
        Set<String> ids=new HashSet<>();
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select mensaje_id from p1_correo_procesado union select mensaje_id from p1_correo_control")){while(r.next())ids.add(r.getString(1));}
        return ids;
    }
    public void registrarEntrada(String id,String remitente,String asunto)throws SQLException{
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_correo_control(mensaje_id,remitente,asunto,estado) values(?,?,?,'RECIBIDO') on conflict(mensaje_id) do nothing")){
            p.setString(1,id);p.setString(2,remitente);p.setString(3,asunto);p.executeUpdate();
        }
    }
    public List<CorreoControl> correosPendientes(int limite)throws SQLException{
        java.util.ArrayList<CorreoControl> out=new java.util.ArrayList<>();
        String sql="select mensaje_id,remitente,asunto,estado,respuesta_asunto,respuesta_cuerpo,exito,intentos from p1_correo_control where estado<>'RESPONDIDO' and siguiente_intento<=now() order by creado_en limit ?";
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,limite);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new CorreoControl(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),(Boolean)r.getObject(7),r.getInt(8)));}}
        return out;
    }
    public void guardarRespuesta(String id,Resultado r)throws SQLException{
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("update p1_correo_control set estado='RESPUESTA_PENDIENTE',respuesta_asunto=?,respuesta_cuerpo=?,exito=?,ultimo_error=null,siguiente_intento=now(),actualizado_en=now() where mensaje_id=? and estado='RECIBIDO'")){
            p.setString(1,r.asunto());p.setString(2,r.cuerpo());p.setBoolean(3,r.exito());p.setString(4,id);p.executeUpdate();
        }
    }
    public void confirmarRespuesta(String id)throws SQLException{
        try(Connection c=ds.getConnection()){c.setAutoCommit(false);try{
            try(PreparedStatement p=c.prepareStatement("update p1_correo_control set estado='RESPONDIDO',ultimo_error=null,actualizado_en=now() where mensaje_id=?")){p.setString(1,id);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("insert into p1_correo_procesado(mensaje_id,remitente,asunto,exito) select mensaje_id,remitente,asunto,coalesce(exito,false) from p1_correo_control where mensaje_id=? on conflict(mensaje_id) do nothing")){p.setString(1,id);p.executeUpdate();}
            c.commit();
        }catch(SQLException ex){c.rollback();throw ex;}}
    }
    public void registrarFallo(String id,String error,int maxSegundos)throws SQLException{
        int intentos=1;try(Connection c=ds.getConnection();PreparedStatement q=c.prepareStatement("select intentos from p1_correo_control where mensaje_id=?")){q.setString(1,id);try(ResultSet r=q.executeQuery()){if(r.next())intentos=r.getInt(1)+1;}}
        long demora=Math.min(maxSegundos,Math.max(2L,1L<<Math.min(intentos,8)));
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("update p1_correo_control set intentos=?,ultimo_error=?,siguiente_intento=now()+(?||' seconds')::interval,actualizado_en=now() where mensaje_id=?")){
            p.setInt(1,intentos);p.setString(2,limitar(error,1000));p.setLong(3,demora);p.setString(4,id);p.executeUpdate();
        }
    }
    private static String limitar(String s,int max){if(s==null)return "Error desconocido";return s.length()<=max?s:s.substring(0,max);}

    /** Ejecuta negocio, auditoría y resultado en una sola transacción. El mensaje es la clave idempotente. */
    @Override public Resultado ejecutarIdempotente(String mensajeId,String remitente,Orden orden)throws Exception{
        try(Connection real=ds.getConnection()){
            real.setAutoCommit(false);transaccion.set(real);
            try{
                try(PreparedStatement q=real.prepareStatement("select exito,respuesta_asunto,respuesta_cuerpo from p1_resultado_orden where mensaje_id=?")){q.setString(1,mensajeId);try(ResultSet r=q.executeQuery()){if(r.next()){real.commit();return new Resultado(r.getBoolean(1),r.getString(2),r.getString(3));}}}
                Resultado resultado=ejecutar(remitente,orden);
                try(PreparedStatement p=real.prepareStatement("insert into p1_resultado_orden(mensaje_id,exito,respuesta_asunto,respuesta_cuerpo) values(?,?,?,?)")){p.setString(1,mensajeId);p.setBoolean(2,resultado.exito());p.setString(3,resultado.asunto());p.setString(4,resultado.cuerpo());p.executeUpdate();}
                real.commit();return resultado;
            }catch(Exception ex){real.rollback();throw ex;}finally{transaccion.remove();}
        }
    }
    private Connection conexion()throws SQLException{
        Connection real=transaccion.get();if(real==null)return ds.getConnection();
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            String n=method.getName();if(n.equals("close")||n.equals("commit")||n.equals("rollback")||n.equals("setAutoCommit"))return null;
            try{return method.invoke(real,args);}catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
        });
    }
    @Override public boolean autorizado(String correo,String rol)throws SQLException{
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("select 1 from p1_usuario where lower(correo)=lower(?) and (rol=? or (?='DECANO' and rol='VICEDECANO')) and activo=true")){p.setString(1,correo);p.setString(2,rol);p.setString(3,rol);return p.executeQuery().next();}
    }
    @Override public boolean hayUsuarios()throws SQLException{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select 1 from p1_usuario limit 1")){return r.next();}}
    @Override public boolean correoProcesado(String id)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("select 1 from p1_correo_procesado where mensaje_id=?")){p.setString(1,id);return p.executeQuery().next();}}
    @Override public Set<String> correosProcesados()throws SQLException{Set<String> ids=new HashSet<>();try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select mensaje_id from p1_correo_procesado")){while(r.next())ids.add(r.getString(1));}return ids;}
    @Override public boolean servicioInicializado()throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("select 1 from p1_servicio_estado where clave='bandeja_inicializada' and valor='true'")){return p.executeQuery().next();}}
    @Override public void marcarServicioInicializado()throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_servicio_estado(clave,valor) values('bandeja_inicializada','true') on conflict(clave) do update set valor=excluded.valor,actualizado_en=now()")){p.executeUpdate();}}
    @Override public void registrarCorreoProcesado(String id,String remitente,String asunto,boolean exito)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_correo_procesado(mensaje_id,remitente,asunto,exito) values(?,?,?,?) on conflict(mensaje_id) do nothing")){p.setString(1,id);p.setString(2,remitente);p.setString(3,asunto);p.setBoolean(4,exito);p.executeUpdate();}}
    @Override public Resultado ejecutar(String remitente,Orden o)throws Exception{
        Resultado resultado=switch(o.comando()){
            case "INSUSU"->insertarUsuario(o.parametros()); case "LISUSU"->listarUsuarios();
            case "INSCON"->insertarConvocatoria(o.parametros()); case "LISCON"->listarConvocatorias();
            case "INSCAR"->insertarCargo(o.parametros()); case "LISCAR"->listarCargos();
            case "INSPOST"->insertarPostulacion(remitente,o.parametros()); case "LISPOST"->listarPostulaciones(remitente);
            case "EVAREQ"->evaluarRequisito(remitente,o.parametros()); case "HABPOS"->habilitar(o.parametros());
            case "REGMER"->registrarMerito(remitente,o.parametros()); case "REGEXA"->registrarExamen(remitente,o.parametros());
            case "ASIEVA"->asignarEvaluador(o.parametros()); case "GENRAN"->generarRanking(o.parametros().get(0));
            case "ASIPLA"->asignarPlaza(o.parametros()); case "AUDCAL"->auditoriaProyecto(o.parametros());
            case "REPEST"->estadisticas(o.parametros().get(0)); default->Resultado.error("Comando sin implementación.");};
        if(resultado.exito()&&java.util.Set.of("INSUSU","INSCON","INSCAR","INSPOST","EVAREQ","HABPOS","REGMER","REGEXA","ASIEVA","ASIPLA").contains(o.comando()))registrarAuditoria(remitente,o);
        return resultado;
    }
    private Resultado insertarUsuario(List<String> p)throws SQLException{return actualizar("insert into p1_usuario(registro,nombres,apellidos,rol,telefono,correo,activo) values(?,?,?,?,?,?,?::boolean)",p,"Usuario creado.");}
    private Resultado listarUsuarios()throws SQLException{return consultar("select registro,nombres,apellidos,rol,correo,activo from p1_usuario order by apellidos,nombres");}
    private Resultado insertarConvocatoria(List<String> p)throws SQLException{return actualizar("insert into p1_convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas) values(?,?,?::date,?::date,?,?::int)",p,"Convocatoria creada.");}
    private Resultado listarConvocatorias()throws SQLException{return consultar("select codigo,titulo,fecha_inicio,fecha_fin,estado,plazas from p1_convocatoria order by fecha_inicio desc");}
    private Resultado insertarCargo(List<String> p)throws SQLException{return actualizar("insert into p1_cargo(nombre,unidad,activo) values(?,?,?::boolean)",p,"Cargo creado.");}
    private Resultado listarCargos()throws SQLException{return consultar("select id,nombre,unidad,activo from p1_cargo order by unidad,nombre");}
    private Resultado insertarPostulacion(String correo,List<String> p)throws SQLException{try(Connection c=conexion()){c.setAutoCommit(false);try(PreparedStatement s=c.prepareStatement("insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id,estado) select ?::bigint,u.id,?::bigint,'REGISTRADA' from p1_usuario u where lower(u.correo)=lower(?) and u.activo=true returning id")){s.setString(1,p.get(0));s.setString(2,p.get(1));s.setString(3,correo);ResultSet r=s.executeQuery();if(!r.next()){c.rollback();return Resultado.error("No existe un postulante activo asociado al correo remitente.");}long id=r.getLong(1);try(PreparedStatement e=c.prepareStatement("insert into p1_expediente(postulacion_id,descripcion,ruta_archivo,tipo_mime) values(?,'Expediente digital',?,'application/pdf')")){e.setLong(1,id);e.setString(2,p.get(2));e.executeUpdate();}c.commit();return Resultado.ok("Postulación y expediente registrados. ID: "+id);}catch(SQLException ex){c.rollback();throw ex;}}}
    private Resultado listarPostulaciones(String correo)throws SQLException{return consultar("select p.id,c.codigo,p.estado,p.puntaje_meritos,p.puntaje_examen,p.puntaje_final,p.plaza_asignada from p1_postulacion p join p1_convocatoria c on c.id=p.convocatoria_id join p1_usuario u on u.id=p.postulante_id where lower(u.correo)=lower(?) order by p.id desc",List.of(correo));}
    private Resultado evaluarRequisito(String correo,List<String> p)throws SQLException{return actualizar("insert into p1_evaluacion_requisito(postulacion_id,requisito,cumple,observacion,evaluador_correo) values(?::bigint,?,?::boolean,?,?)",List.of(p.get(0),p.get(1),p.get(2),p.get(3),correo),"Requisito evaluado.");}
    private Resultado habilitar(List<String> p)throws SQLException{return actualizar("update p1_postulacion set estado=?,observacion=? where id=?::bigint",List.of(p.get(1),p.get(2),p.get(0)),"Estado de postulación actualizado.");}
    private Resultado registrarMerito(String correo,List<String> p)throws SQLException{Resultado r=actualizar("insert into p1_merito(postulacion_id,criterio,puntaje,observacion,evaluador_correo) values(?::bigint,?,?::numeric,?,?)",List.of(p.get(0),p.get(1),p.get(2),p.get(3),correo),"Mérito registrado.");if(r.exito())recalcularPuntaje(p.get(0));return r;}
    private Resultado registrarExamen(String correo,List<String> p)throws SQLException{Resultado r=actualizar("insert into p1_examen(postulacion_id,puntaje,evaluador_correo,observacion) values(?::bigint,?::numeric,?,?)",List.of(p.get(0),p.get(1),correo,p.get(2)),"Examen registrado.");if(r.exito())recalcularPuntaje(p.get(0));return r;}
    private Resultado asignarEvaluador(List<String> p)throws SQLException{return actualizar("insert into p1_asignacion_evaluador(postulacion_id,evaluador_id,tipo) select ?::bigint,u.id,? from p1_usuario u where u.id=?::bigint and u.rol='EVALUADOR' and u.activo=true",List.of(p.get(0),p.get(2),p.get(1)),"Evaluador asignado.");}
    private Resultado generarRanking(String convocatoria)throws SQLException{return consultar("select u.registro,u.nombres,u.apellidos,p.puntaje_final,row_number() over(order by p.puntaje_final desc,p.id) posicion from p1_postulacion p join p1_usuario u on u.id=p.postulante_id where p.convocatoria_id=?::bigint order by posicion",List.of(convocatoria));}
    private Resultado asignarPlaza(List<String> p)throws SQLException{return actualizar("update p1_postulacion set plaza_asignada=?::boolean,estado=case when ?::boolean then 'GANADOR' else 'HABILITADO' end where id=?::bigint",List.of(p.get(1),p.get(1),p.get(0)),"Asignación de plaza actualizada.");}
    private Resultado auditoriaProyecto(List<String> p)throws SQLException{return consultar("select fecha,actor,accion,detalle from p1_auditoria where entidad=? and entidad_id=? order by fecha desc",p);}
    private Resultado estadisticas(String convocatoria)throws SQLException{return consultar("select estado,count(*) cantidad,round(avg(puntaje_final),2) promedio from p1_postulacion where convocatoria_id=?::bigint group by estado order by estado",List.of(convocatoria));}
    private Resultado actualizar(String sql,List<String> p,String ok)throws SQLException{try(Connection c=conexion();PreparedStatement s=c.prepareStatement(sql)){s.setQueryTimeout(30);for(int i=0;i<p.size();i++)s.setString(i+1,p.get(i));int n=s.executeUpdate();return n>0?Resultado.ok(ok+" Filas afectadas: "+n):Resultado.error("La operación no afectó registros.");}}
    private Resultado consultar(String sql)throws SQLException{return consultar(sql,List.of());}
    private Resultado consultar(String sql,List<String> parametros)throws SQLException{StringBuilder b=new StringBuilder();try(Connection c=conexion();PreparedStatement s=c.prepareStatement(sql)){s.setQueryTimeout(30);s.setMaxRows(201);for(int i=0;i<parametros.size();i++)s.setString(i+1,parametros.get(i));try(ResultSet r=s.executeQuery()){ResultSetMetaData m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++){if(i>1)b.append(" | ");b.append(m.getColumnLabel(i));}b.append('\n');int n=0;while(r.next()){if(n==200){b.append("... resultado limitado a 200 filas\n");break;}for(int i=1;i<=m.getColumnCount();i++){if(i>1)b.append(" | ");b.append(r.getString(i));}b.append('\n');n++;}if(n==0)return Resultado.ok("Sin resultados.");}}return Resultado.ok(b.toString());}
    private void recalcularPuntaje(String postulacion)throws SQLException{actualizar("update p1_postulacion p set puntaje_meritos=coalesce((select sum(m.puntaje) from p1_merito m where m.postulacion_id=p.id),0),puntaje_examen=coalesce((select avg(e.puntaje) from p1_examen e where e.postulacion_id=p.id),0) where p.id=?::bigint",List.of(postulacion),"Puntaje recalculado");try(Connection c=conexion();PreparedStatement s=c.prepareStatement("update p1_postulacion set puntaje_final=round(puntaje_meritos*0.4+puntaje_examen*0.6,2) where id=?::bigint")){s.setString(1,postulacion);s.executeUpdate();}}
    private void registrarAuditoria(String actor,Orden o)throws SQLException{try(Connection c=conexion();PreparedStatement s=c.prepareStatement("insert into p1_auditoria(actor,accion,entidad,entidad_id,detalle) values(?,?,?,?,?)")){s.setString(1,actor);s.setString(2,o.comando());s.setString(3,o.comando());s.setString(4,o.parametros().isEmpty()?null:o.parametros().get(0));s.setString(5,"Orden ejecutada correctamente con "+o.parametros().size()+" parámetros.");s.executeUpdate();}}
}

