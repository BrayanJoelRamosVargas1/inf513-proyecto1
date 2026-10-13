package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.*;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

public final class Main {
    private static final Pattern CORREO_SEGURO=Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9.-]{1,253}$");
    private Main(){}
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();
        AtomicBoolean activo=new AtomicBoolean(true);
        Thread principal=Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{activo.set(false);principal.interrupt();},"apagado-seguro"));

        HikariConfig hc=new HikariConfig();
        hc.setJdbcUrl(cfg.dbUrl());hc.setUsername(cfg.dbUser());hc.setPassword(cfg.dbPassword());
        hc.setMaximumPoolSize(4);hc.setMinimumIdle(0);hc.setInitializationFailTimeout(-1);
        hc.setConnectionTimeout(cfg.connectSeconds()*1000L);hc.setValidationTimeout(Math.min(5_000L,cfg.connectSeconds()*1000L));
        hc.setKeepaliveTime(30_000L);hc.setMaxLifetime(300_000L);
        hc.addDataSourceProperty("connectTimeout",cfg.connectSeconds());hc.addDataSourceProperty("socketTimeout",cfg.readSeconds());hc.addDataSourceProperty("tcpKeepAlive",true);

        try(HikariDataSource ds=new HikariDataSource(hc);PasarelaCorreo mail=new PasarelaJakartaMail(cfg)){
            AnalizadorOrden parser=new AnalizadorOrden();RepositorioPostgres repo=new RepositorioPostgres(ds);ProcesadorOrden processor=new ProcesadorOrden(new CatalogoComandos(),repo,cfg.bootstrapAdminEmail());
            int fallosConsecutivos=0;
            Connection bloqueoInstancia=null;
            System.out.println("INF513 Grupo 09 SA - servicio resiliente iniciado. Ctrl+C para detener.");
            while(activo.get()){
                try{bloqueoInstancia=asegurarInstanciaUnica(ds,bloqueoInstancia);ejecutarCiclo(cfg,mail,repo,processor,parser);fallosConsecutivos=0;dormir(activo,cfg.pollSeconds());}
                catch(InterruptedException ex){Thread.currentThread().interrupt();break;}
                catch(Exception ex){if(bloqueoInstancia!=null&&!valida(bloqueoInstancia)){try{bloqueoInstancia.close();}catch(SQLException cierre){ex.addSuppressed(cierre);}bloqueoInstancia=null;}fallosConsecutivos++;long espera=esperaReintento(fallosConsecutivos,cfg.pollSeconds(),cfg.retryMaxSeconds());System.err.println("Servicio temporalmente no disponible. Reintento "+fallosConsecutivos+" en "+espera+" s: "+seguro(ex));dormir(activo,espera);}
            }
            if(bloqueoInstancia!=null)bloqueoInstancia.close();
        }
        System.out.println("Servicio detenido de forma segura.");
    }

    static void ejecutarCiclo(AppConfig cfg,PasarelaCorreo mail,RepositorioPostgres repo,ProcesadorOrden processor,AnalizadorOrden parser)throws Exception{
        if(!repo.servicioInicializado(cfg.inboundProtocol())){
            var ids=mail.identificadores();for(String id:ids)repo.registrarCorreoProcesado(id,"historico@local","BASELINE",true);repo.marcarServicioInicializado();
            repo.marcarServicioInicializado(cfg.inboundProtocol());
            System.out.println("Bandeja inicializada: "+ids.size()+" mensajes históricos omitidos.");return;
        }
        try{for(MensajeEntrada msg:mail.noProcesados(repo::correosConocidos)){
            if(!idSeguro(msg.id())){
                System.err.println("Correo descartado por identificador inválido.");continue;
            }
            String asunto=normalizarAsunto(msg.asunto());
            if(!correoSeguro(msg.remitente())){repo.registrarCorreoProcesado(msg.id(),"invalido@local",asunto,false);System.err.println("Correo descartado por remitente inválido.");continue;}
            try{if(repo.registrarEntrada(msg.id(),msg.remitente(),asunto)!=RepositorioPostgres.Admision.SATURADO)mail.marcarProcesado(msg);}
            catch(Exception ex){System.err.println("No se pudo registrar un correo; los demás continuarán: "+seguro(ex));}
        }}finally{mail.liberarReferencias();}
        repo.promoverDiferidos(cfg.maxMessagesPerCycle());
        for(var pendiente:repo.correosPendientes(cfg.maxMessagesPerCycle())){
            try{
                if("RECIBIDO".equals(pendiente.estado())){
                    Resultado resultado;try{resultado=processor.procesar(pendiente.id(),pendiente.remitente(),parser.analizar(pendiente.asunto()));}catch(OrdenInvalidaException ex){resultado=Resultado.error(ex.getMessage());}
                    repo.guardarRespuesta(pendiente.id(),resultado);continue;
                }
                MensajeEntrada original=new MensajeEntrada(pendiente.id(),pendiente.remitente(),pendiente.asunto());
                mail.responder(original,pendiente.respuestaAsunto(),pendiente.respuestaCuerpo());repo.confirmarRespuesta(pendiente.id());mail.marcarProcesado(original);
                System.out.println("Respondido correo "+pendiente.id()+" de "+pendiente.remitente()+". Éxito: "+pendiente.exito());
            }catch(Exception ex){
                try{repo.registrarFallo(pendiente.id(),seguro(ex),cfg.retryMaxSeconds());}catch(Exception registro){System.err.println("No se pudo persistir el fallo: "+seguro(registro));}
                System.err.println("Correo "+pendiente.id()+" pendiente de reintento: "+seguro(ex));
            }
        }
    }
    static long esperaReintento(int fallos,int base,int max){long exp=Math.min(max,Math.max(base,1L<<Math.min(fallos,8)));long jitter=ThreadLocalRandom.current().nextLong(Math.max(1,exp/4+1));return Math.min(max,exp+jitter);}
    private static Connection asegurarInstanciaUnica(HikariDataSource ds,Connection actual)throws SQLException{
        if(valida(actual))return actual;
        if(actual!=null)actual.close();
        Connection c=ds.getConnection();
        try(var s=c.prepareStatement("select pg_try_advisory_lock(?)")){s.setLong(1,5130909L);try(var r=s.executeQuery()){if(!r.next()||!r.getBoolean(1)){c.close();throw new SQLException("Ya existe otra instancia activa del servicio");}}}
        return c;
    }
    private static boolean valida(Connection c){try{return c!=null&&!c.isClosed()&&c.isValid(3);}catch(SQLException ex){return false;}}
    static boolean idSeguro(String id){return id!=null&&!id.isBlank()&&id.length()<=255&&id.chars().noneMatch(Character::isISOControl);}
    static String normalizarAsunto(String asunto){if(asunto==null)return "";return asunto.length()>998?"RECHAZADO: ASUNTO DEMASIADO LARGO":asunto;}
    static boolean correoSeguro(String correo){return correo!=null&&correo.length()<=160&&!correo.contains("..")&&CORREO_SEGURO.matcher(correo).matches();}
    private static void dormir(AtomicBoolean activo,long segundos)throws InterruptedException{if(activo.get())Thread.sleep(Math.max(1,segundos)*1000L);}
    private static String seguro(Throwable ex){String m=ex.getMessage();if(m==null)m=ex.getClass().getSimpleName();return limitar(m.replaceAll("(?i)(password|contraseña)\\s*[=:]\\s*\\S+","$1=[OCULTO]"),500);}
    private static String limitar(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n);}
}
