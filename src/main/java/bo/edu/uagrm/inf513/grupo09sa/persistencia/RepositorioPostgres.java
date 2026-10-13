package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.Orden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RepositorioPostgres implements RepositorioProyecto {
    private final DataSource ds;
    private final ThreadLocal<Connection> transaccion=new ThreadLocal<>();
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final SecureRandom ALEATORIO=new SecureRandom();
    public RepositorioPostgres(DataSource ds){this.ds=ds;}
    public record CorreoControl(String id,String remitente,String asunto,String estado,String respuestaAsunto,String respuestaCuerpo,Boolean exito,int intentos){}

    /** Consulta por lotes sólo los IDs presentes en la página actual; el historial total nunca se carga en memoria. */
    public Set<String> correosConocidos(Collection<String> candidatos)throws SQLException{
        if(candidatos==null||candidatos.isEmpty())return Set.of();
        if(candidatos.size()>1000)throw new SQLException("Lote de identificadores demasiado grande");
        Set<String> ids=new HashSet<>();String marcas=String.join(",",java.util.Collections.nCopies(candidatos.size(),"?"));
        String sql="select mensaje_id from p1_correo_procesado where mensaje_id in ("+marcas+") union select mensaje_id from p1_correo_control where mensaje_id in ("+marcas+")";
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){int i=1;for(String id:candidatos)p.setString(i++,id);for(String id:candidatos)p.setString(i++,id);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}}
        return ids;
    }
    public enum Admision { ADMITIDO, DIFERIDO, DUPLICADO, SATURADO }
    /** La saturación es temporal: conserva la orden en la cola o en la bandeja. */
    public Admision registrarEntrada(String id,String remitente,String asunto)throws SQLException{
        String sql="insert into p1_correo_control(mensaje_id,remitente,asunto,estado,admitido_en) select ?,?,?,'DIFERIDO',null where (select count(*) from p1_correo_control where estado<>'RESPONDIDO')<10000 on conflict(mensaje_id) do nothing";
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,id);p.setString(2,remitente);p.setString(3,asunto);
            if(p.executeUpdate()==1)return Admision.DIFERIDO;
            try(PreparedStatement q=c.prepareStatement("select 1 from p1_correo_control where mensaje_id=? union select 1 from p1_correo_procesado where mensaje_id=?")){q.setString(1,id);q.setString(2,id);try(ResultSet r=q.executeQuery()){return r.next()?Admision.DUPLICADO:Admision.SATURADO;}}
        }
    }
    public int promoverDiferidos(int limite)throws SQLException{
        String sql="with tasas as (select lower(remitente) remitente,count(*) n from p1_correo_control where admitido_en>now()-interval '1 hour' group by lower(remitente)), candidatos as (select c.mensaje_id,c.creado_en,row_number() over(partition by lower(c.remitente) order by c.creado_en,c.mensaje_id) posicion,60-coalesce(t.n,0) cupo from p1_correo_control c left join tasas t on t.remitente=lower(c.remitente) where c.estado='DIFERIDO'), elegidos as (select mensaje_id from candidatos where posicion<=cupo order by posicion,creado_en,mensaje_id limit least(?,greatest(0,1000-(select count(*) from p1_correo_control where admitido_en>now()-interval '1 hour')))) update p1_correo_control set estado='RECIBIDO',admitido_en=now(),actualizado_en=now() where mensaje_id in(select mensaje_id from elegidos)";
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,limite);return p.executeUpdate();}
    }
    public List<CorreoControl> correosPendientes(int limite)throws SQLException{
        java.util.ArrayList<CorreoControl> out=new java.util.ArrayList<>();
        String sql="select mensaje_id,remitente,asunto,estado,respuesta_asunto,respuesta_cuerpo,exito,intentos from p1_correo_control where estado in('RECIBIDO','RESPUESTA_PENDIENTE') and siguiente_intento<=now() order by intentos asc,siguiente_intento asc,creado_en asc limit ?";
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
    private static String limitarDato(String s,int max){if(s==null)return "NULL";return s.length()<=max?s:s.substring(0,max)+"…";}

    /** Ejecuta negocio, auditoría y resultado en una sola transacción. El mensaje es la clave idempotente. */
    @Override public Resultado ejecutarIdempotente(String mensajeId,String remitente,Orden orden)throws Exception{
        if(transaccion.get()!=null)return ejecutarEnTransaccion(transaccion.get(),mensajeId,remitente,orden);
        try(Connection real=ds.getConnection()){
            real.setAutoCommit(false);transaccion.set(real);
            try{
                Resultado resultado=ejecutarEnTransaccion(real,mensajeId,remitente,orden);
                real.commit();return resultado;
            }catch(Exception ex){real.rollback();throw ex;}finally{transaccion.remove();}
        }
    }
    private Resultado ejecutarEnTransaccion(Connection c,String id,String remitente,Orden orden)throws Exception{
        Resultado anterior=resultadoGuardado(c,id);if(anterior!=null)return anterior;
        Resultado resultado=ejecutar(remitente,orden);guardarResultado(c,id,resultado);return resultado;
    }
    private static Resultado resultadoGuardado(Connection c,String id)throws SQLException{
        try(PreparedStatement q=c.prepareStatement("select exito,respuesta_asunto,respuesta_cuerpo from p1_resultado_orden where mensaje_id=?")){q.setString(1,id);try(ResultSet r=q.executeQuery()){return r.next()?new Resultado(r.getBoolean(1),r.getString(2),r.getString(3)):null;}}
    }
    private static void guardarResultado(Connection c,String id,Resultado resultado)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("insert into p1_resultado_orden(mensaje_id,exito,respuesta_asunto,respuesta_cuerpo) values(?,?,?,?)")){p.setString(1,id);p.setBoolean(2,resultado.exito());p.setString(3,resultado.asunto());p.setString(4,resultado.cuerpo());p.executeUpdate();}
    }
    @Override public Resultado solicitarConfirmacion(String mensajeId,String remitente,Orden orden)throws Exception{
        String id=mensajeId==null?"local:"+UUID.randomUUID():mensajeId;
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement lock=c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")){lock.setString(1,id);lock.execute();}
                try(PreparedStatement q=c.prepareStatement("select respuesta_asunto,respuesta_cuerpo,exito from p1_correo_control where mensaje_id=? and estado='RESPUESTA_PENDIENTE'")){q.setString(1,id);try(ResultSet r=q.executeQuery()){if(r.next()){c.commit();return new Resultado(r.getBoolean(3),r.getString(1),r.getString(2));}}}
                byte[] bytes=new byte[32];ALEATORIO.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                try(PreparedStatement p=c.prepareStatement("insert into p1_confirmacion(mensaje_id,remitente,token_hash,orden_json) values(?,?,?,?) on conflict(mensaje_id) do update set token_hash=excluded.token_hash,vence_en=now()+interval '30 minutes' where not p1_confirmacion.usado")){p.setString(1,id);p.setString(2,remitente);p.setString(3,hash(token));p.setString(4,JSON.writeValueAsString(orden));if(p.executeUpdate()!=1){c.rollback();return Resultado.error("La orden ya fue confirmada.");}}
                Resultado respuesta=new Resultado(false,"CONFIRMACIÓN REQUERIDA - "+orden.comando(),"Orden pendiente: "+JSON.writeValueAsString(orden)+"\nSi solicitó esta operación, envíe desde esta misma cuenta el asunto:\nCONFIRMAR[\""+token+"\"]\nEl código vence en 30 minutos y solo autoriza esta orden. Si no la solicitó, ignore este mensaje.");
                try(PreparedStatement p=c.prepareStatement("update p1_correo_control set estado='RESPUESTA_PENDIENTE',respuesta_asunto=?,respuesta_cuerpo=?,exito=false,actualizado_en=now() where mensaje_id=? and estado='RECIBIDO'")){p.setString(1,respuesta.asunto());p.setString(2,respuesta.cuerpo());p.setString(3,id);p.executeUpdate();}
                c.commit();return respuesta;
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
    @Override public Resultado confirmarOrden(String mensajeId,String remitente,String token,OrdenConfirmada ejecutar)throws Exception{
        if(mensajeId==null||!token.matches("[A-Za-z0-9_-]{43}"))return Resultado.error("Confirmación inválida.");
        try(Connection c=ds.getConnection()){
            c.setAutoCommit(false);transaccion.set(c);
            try{
                Resultado respuesta;
                Timestamp recibido=null;
                try(PreparedStatement entrada=c.prepareStatement("select creado_en,asunto from p1_correo_control where mensaje_id=? and lower(remitente)=lower(?)")){
                    entrada.setString(1,mensajeId);entrada.setString(2,remitente);
                    try(ResultSet fila=entrada.executeQuery()){if(fila.next()){
                        try{Orden guardada=new bo.edu.uagrm.inf513.grupo09sa.comandos.AnalizadorOrden().analizar(fila.getString(2));
                            if(guardada.comando().equals("CONFIRMAR")&&guardada.parametros().equals(List.of(token)))recibido=fila.getTimestamp(1);
                        }catch(bo.edu.uagrm.inf513.grupo09sa.comandos.OrdenInvalidaException ignorada){}
                    }}
                }
                try(PreparedStatement q=c.prepareStatement("select mensaje_id,orden_json,usado,confirmacion_id,vence_en>coalesce(?::timestamptz,now()) vigente from p1_confirmacion where token_hash=? and lower(remitente)=lower(?) for update")){
                    q.setTimestamp(1,recibido);q.setString(2,hash(token));q.setString(3,remitente);
                    try(ResultSet r=q.executeQuery()){
                        if(!r.next()){c.rollback();return Resultado.error("Código inválido, vencido o ya utilizado.");}
                        String original=r.getString(1);
                        if(r.getBoolean(3)){
                            if(!mensajeId.equals(r.getString(4))){c.rollback();return Resultado.error("Código inválido, vencido o ya utilizado.");}
                            respuesta=resultadoGuardado(c,"confirmacion:"+hash(original));c.commit();return respuesta;
                        }
                        if(!r.getBoolean(5)){c.rollback();return Resultado.error("Código inválido, vencido o ya utilizado.");}
                        Savepoint negocio=c.setSavepoint();
                        respuesta=ejecutar.ejecutar(original,JSON.readValue(r.getString(2),Orden.class));
                        if(!respuesta.exito())c.rollback(negocio);
                        guardarResultado(c,"confirmacion:"+hash(original),respuesta);
                        try(PreparedStatement p=c.prepareStatement("update p1_confirmacion set usado=true,confirmacion_id=? where mensaje_id=?")){p.setString(1,mensajeId);p.setString(2,original);p.executeUpdate();}
                    }
                }
                c.commit();return respuesta;
            }catch(Exception ex){c.rollback();throw ex;}finally{transaccion.remove();}
        }
    }
    private static String hash(String token)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
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
    public boolean servicioInicializado(String canal)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("select 1 from p1_servicio_estado where clave=? and valor='true'")){p.setString(1,"bandeja_inicializada_"+canal.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","_"));return p.executeQuery().next();}}
    @Override public void marcarServicioInicializado()throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_servicio_estado(clave,valor) values('bandeja_inicializada','true') on conflict(clave) do update set valor=excluded.valor,actualizado_en=now()")){p.executeUpdate();}}
    public void marcarServicioInicializado(String canal)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_servicio_estado(clave,valor) values(?,'true') on conflict(clave) do update set valor=excluded.valor,actualizado_en=now()")){p.setString(1,"bandeja_inicializada_"+canal.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","_"));p.executeUpdate();}}
    @Override public void registrarCorreoProcesado(String id,String remitente,String asunto,boolean exito)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("insert into p1_correo_procesado(mensaje_id,remitente,asunto,exito) values(?,?,?,?) on conflict(mensaje_id) do nothing")){p.setString(1,id);p.setString(2,remitente);p.setString(3,asunto);p.setBoolean(4,exito);p.executeUpdate();}}
    @Override public Resultado ejecutar(String remitente,Orden o)throws Exception{
        Resultado resultado=switch(o.comando()){
            case "INSUSU"->insertarUsuario(o.parametros()); case "LISUSU"->listarUsuarios(o.parametros().get(0));
            case "INSCON"->insertarConvocatoria(o.parametros()); case "LISCON"->listarConvocatorias(o.parametros().get(0));
            case "INSCAR"->insertarCargo(o.parametros()); case "LISCAR"->listarCargos(o.parametros().get(0));
            case "INSPOST"->insertarPostulacion(remitente,o.parametros()); case "LISPOST"->listarPostulaciones(remitente,o.parametros().get(0));
            case "EVAREQ"->evaluarRequisito(remitente,o.parametros()); case "HABPOS"->habilitar(o.parametros());
            case "REGMER"->registrarMerito(remitente,o.parametros()); case "REGEXA"->registrarExamen(remitente,o.parametros());
            case "ASIEVA"->asignarEvaluador(o.parametros()); case "GENRAN"->generarRanking(o.parametros().get(0));
            case "ASIPLA"->asignarPlaza(o.parametros()); case "AUDCAL"->auditoriaProyecto(o.parametros());
            case "REPEST"->estadisticas(o.parametros().get(0)); default->Resultado.error("Comando sin implementación.");};
        if(resultado.exito()&&java.util.Set.of("INSUSU","INSCON","INSCAR","INSPOST","EVAREQ","HABPOS","REGMER","REGEXA","ASIEVA","ASIPLA").contains(o.comando()))registrarAuditoria(remitente,o);
        return resultado;
    }
    private Resultado insertarUsuario(List<String> p)throws SQLException{return actualizar("insert into p1_usuario(registro,nombres,apellidos,rol,telefono,correo,activo) values(?,?,?,?,?,?,?::boolean)",p,"Usuario creado.");}
private Resultado listarUsuarios(String filtro)throws SQLException{return consultar("select id,registro,nombres,apellidos,rol,correo,activo from p1_usuario where ?='*' or concat_ws(' ',registro,nombres,apellidos,rol,correo) ilike ? escape '\\' order by apellidos,nombres",List.of(filtro,patron(filtro)));}
    private Resultado insertarConvocatoria(List<String> p)throws SQLException{return actualizar("insert into p1_convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas) values(?,?,?::date,?::date,?,?::int)",p,"Convocatoria creada.");}
    private Resultado listarConvocatorias(String filtro)throws SQLException{return consultar("select id,codigo,titulo,fecha_inicio,fecha_fin,estado,plazas from p1_convocatoria where ?='*' or concat_ws(' ',codigo,titulo,estado) ilike ? escape '\\' order by fecha_inicio desc",List.of(filtro,patron(filtro)));}
    private Resultado insertarCargo(List<String> p)throws SQLException{return actualizar("insert into p1_cargo(nombre,unidad,activo) values(?,?,?::boolean)",p,"Cargo creado.");}
    private Resultado listarCargos(String filtro)throws SQLException{return consultar("select id,nombre,unidad,activo from p1_cargo where ?='*' or concat_ws(' ',id::text,nombre,unidad) ilike ? escape '\\' order by unidad,nombre",List.of(filtro,patron(filtro)));}
    private Resultado insertarPostulacion(String correo,List<String> p)throws SQLException{try(Connection c=conexion()){c.setAutoCommit(false);try(PreparedStatement s=c.prepareStatement("insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id,estado) select co.id,u.id,ca.id,'REGISTRADA' from p1_usuario u join p1_convocatoria co on co.id=?::bigint join p1_cargo ca on ca.id=?::bigint where lower(u.correo)=lower(?) and u.activo=true and u.rol='POSTULANTE' and ca.activo=true and co.estado='ABIERTA' and current_date between co.fecha_inicio and co.fecha_fin returning id")){s.setString(1,p.get(0));s.setString(2,p.get(1));s.setString(3,correo);ResultSet r=s.executeQuery();if(!r.next()){c.rollback();return Resultado.error("La postulación no es válida: verifique postulante, convocatoria abierta y vigente, y cargo activo.");}long id=r.getLong(1);try(PreparedStatement e=c.prepareStatement("insert into p1_expediente(postulacion_id,descripcion,ruta_archivo,tipo_mime) values(?,'Expediente digital',?,'application/pdf')")){e.setLong(1,id);e.setString(2,p.get(2));e.executeUpdate();}c.commit();return Resultado.ok("Postulación y expediente registrados. ID: "+id);}catch(SQLException ex){c.rollback();throw ex;}}}
    private Resultado listarPostulaciones(String correo,String filtro)throws SQLException{return consultar("select p.id,c.codigo,p.estado,p.puntaje_meritos,p.puntaje_examen,p.puntaje_final,p.plaza_asignada from p1_postulacion p join p1_convocatoria c on c.id=p.convocatoria_id join p1_usuario u on u.id=p.postulante_id where lower(u.correo)=lower(?) and (?='*' or concat_ws(' ',p.id::text,c.codigo,p.estado) ilike ? escape '\\') order by p.id desc",List.of(correo,filtro,patron(filtro)));}
    private static String patron(String filtro){return "%"+filtro.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    private Resultado evaluarRequisito(String correo,List<String> p)throws SQLException{return actualizar("insert into p1_evaluacion_requisito(postulacion_id,requisito,cumple,observacion,evaluador_correo) select ?::bigint,?,?::boolean,?,? where exists(select 1 from p1_asignacion_evaluador a join p1_usuario u on u.id=a.evaluador_id where a.postulacion_id=?::bigint and lower(u.correo)=lower(?) and u.activo=true)",List.of(p.get(0),p.get(1),p.get(2),p.get(3),correo,p.get(0),correo),"Requisito evaluado.");}
    private Resultado habilitar(List<String> p)throws SQLException{return actualizar("update p1_postulacion p set estado=?,observacion=? where p.id=?::bigint and (?<>'HABILITADO' or (exists(select 1 from p1_evaluacion_requisito e where e.postulacion_id=p.id) and not exists(select 1 from p1_evaluacion_requisito e where e.postulacion_id=p.id and e.cumple=false)))",List.of(p.get(1),p.get(2),p.get(0),p.get(1)),"Estado de postulación actualizado.");}
    private Resultado registrarMerito(String correo,List<String> p)throws SQLException{Resultado r=actualizar("insert into p1_merito(postulacion_id,criterio,puntaje,observacion,evaluador_correo) select ?::bigint,?,?::numeric,?,? where exists(select 1 from p1_asignacion_evaluador a join p1_usuario u on u.id=a.evaluador_id where a.postulacion_id=?::bigint and lower(u.correo)=lower(?) and u.activo=true)",List.of(p.get(0),p.get(1),p.get(2),p.get(3),correo,p.get(0),correo),"Mérito registrado.");if(r.exito())recalcularPuntaje(p.get(0));return r;}
    private Resultado registrarExamen(String correo,List<String> p)throws SQLException{Resultado r=actualizar("insert into p1_examen(postulacion_id,puntaje,evaluador_correo,observacion) select ?::bigint,?::numeric,?,? where exists(select 1 from p1_asignacion_evaluador a join p1_usuario u on u.id=a.evaluador_id where a.postulacion_id=?::bigint and lower(u.correo)=lower(?) and u.activo=true and exists(select 1 from p1_postulacion p where p.id=a.postulacion_id and p.estado in('HABILITADO','GANADOR')))",List.of(p.get(0),p.get(1),correo,p.get(2),p.get(0),correo),"Examen registrado.");if(r.exito())recalcularPuntaje(p.get(0));return r;}
    private Resultado asignarEvaluador(List<String> p)throws SQLException{return actualizar("insert into p1_asignacion_evaluador(postulacion_id,evaluador_id,tipo) select ?::bigint,u.id,? from p1_usuario u where u.id=?::bigint and u.rol='EVALUADOR' and u.activo=true",List.of(p.get(0),p.get(2),p.get(1)),"Evaluador asignado.");}
    private Resultado generarRanking(String convocatoria)throws SQLException{return consultar("select u.registro,u.nombres,u.apellidos,p.puntaje_final,row_number() over(order by p.puntaje_final desc,p.id) posicion from p1_postulacion p join p1_usuario u on u.id=p.postulante_id where p.convocatoria_id=?::bigint and p.estado in('HABILITADO','GANADOR') order by posicion",List.of(convocatoria));}
    private Resultado asignarPlaza(List<String> p)throws SQLException{
        try(Connection c=conexion()){c.setAutoCommit(false);try{
            long post=Long.parseLong(p.get(0));boolean asignar=Boolean.parseBoolean(p.get(1));long convocatoria;int plazas;
            try(PreparedStatement q=c.prepareStatement("select c.id,c.plazas from p1_postulacion p join p1_convocatoria c on c.id=p.convocatoria_id where p.id=? for update of c")){q.setLong(1,post);try(ResultSet r=q.executeQuery()){if(!r.next()){c.rollback();return Resultado.error("La postulación no existe.");}convocatoria=r.getLong(1);plazas=r.getInt(2);}}
            if(asignar){try(PreparedStatement q=c.prepareStatement("select count(*) from p1_postulacion where convocatoria_id=? and plaza_asignada=true and id<>?")){q.setLong(1,convocatoria);q.setLong(2,post);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>=plazas){c.rollback();return Resultado.error("No quedan plazas disponibles en la convocatoria.");}}}}
            try(PreparedStatement u=c.prepareStatement("update p1_postulacion set plaza_asignada=?,estado=case when ? then 'GANADOR' else case when estado='GANADOR' then 'HABILITADO' else estado end end where id=? and (not ? or estado in('HABILITADO','GANADOR'))")){u.setBoolean(1,asignar);u.setBoolean(2,asignar);u.setLong(3,post);u.setBoolean(4,asignar);if(u.executeUpdate()!=1){c.rollback();return Resultado.error("La postulación debe estar habilitada antes de asignar una plaza.");}}
            c.commit();return Resultado.ok("Asignación de plaza actualizada. Filas afectadas: 1");
        }catch(Exception ex){c.rollback();throw ex;}}
    }
    private Resultado auditoriaProyecto(List<String> p)throws SQLException{return consultar("select fecha,actor,accion,detalle from p1_auditoria where entidad=? and entidad_id=? order by fecha desc",p);}
    private Resultado estadisticas(String convocatoria)throws SQLException{return consultar("select estado,count(*) cantidad,round(avg(puntaje_final),2) promedio from p1_postulacion where convocatoria_id=?::bigint group by estado order by estado",List.of(convocatoria));}
    private Resultado actualizar(String sql,List<String> p,String ok)throws SQLException{try(Connection c=conexion();PreparedStatement s=c.prepareStatement(sql)){s.setQueryTimeout(30);for(int i=0;i<p.size();i++)s.setString(i+1,p.get(i));int n=s.executeUpdate();return n>0?Resultado.ok(ok+" Filas afectadas: "+n):Resultado.error("La operación no afectó registros.");}}
    private Resultado consultar(String sql,List<String> parametros)throws SQLException{StringBuilder b=new StringBuilder();try(Connection c=conexion();PreparedStatement s=c.prepareStatement(sql)){s.setQueryTimeout(30);s.setMaxRows(201);for(int i=0;i<parametros.size();i++)s.setString(i+1,parametros.get(i));try(ResultSet r=s.executeQuery()){ResultSetMetaData m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++){if(i>1)b.append(" | ");b.append(m.getColumnLabel(i));}b.append('\n');int n=0;while(r.next()){if(n==200||b.length()>45_000){b.append("... resultado limitado por seguridad\n");break;}for(int i=1;i<=m.getColumnCount();i++){if(i>1)b.append(" | ");b.append(limitarDato(r.getString(i),500));}b.append('\n');n++;}if(n==0)return Resultado.ok("Sin resultados.");}}return Resultado.ok(limitarDato(b.toString(),50_000));}
    private void recalcularPuntaje(String postulacion)throws SQLException{actualizar("update p1_postulacion p set puntaje_meritos=least(100,coalesce((select sum(m.puntaje) from p1_merito m where m.postulacion_id=p.id),0)),puntaje_examen=coalesce((select avg(e.puntaje) from p1_examen e where e.postulacion_id=p.id),0) where p.id=?::bigint",List.of(postulacion),"Puntaje recalculado");try(Connection c=conexion();PreparedStatement s=c.prepareStatement("update p1_postulacion set puntaje_final=round(least(100,puntaje_meritos*0.4+puntaje_examen*0.6),2) where id=?::bigint")){s.setQueryTimeout(30);s.setString(1,postulacion);s.executeUpdate();}}
    private void registrarAuditoria(String actor,Orden o)throws SQLException{try(Connection c=conexion();PreparedStatement s=c.prepareStatement("insert into p1_auditoria(actor,accion,entidad,entidad_id,detalle) values(?,?,?,?,?)")){s.setString(1,actor);s.setString(2,o.comando());s.setString(3,o.comando());s.setString(4,o.parametros().isEmpty()?null:o.parametros().get(0));s.setString(5,"Orden ejecutada correctamente con "+o.parametros().size()+" parámetros.");s.executeUpdate();}}
}

