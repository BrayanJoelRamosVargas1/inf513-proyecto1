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
import bo.edu.uagrm.inf513.grupo09sa.presentacion.ControladorCorreo;

public final class Main {
    private Main(){}
    public static void main(String[] args)throws Exception{
        BitacoraArchivos.sistema().acceso("INICIO", "-", "-", "SERVICIO", true);
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
            ControladorCorreo controlador=new ControladorCorreo(cfg.inboundProtocol(),cfg.maxMessagesPerCycle(),
                    cfg.retryMaxSeconds(),mail,repo,processor,parser,AntivirusPdf.desdeEntorno());
            int fallosConsecutivos=0;
            Connection bloqueoInstancia=null;
            System.out.println("INF513 Grupo 09 SA - servicio resiliente iniciado. Ctrl+C para detener.");
            while(activo.get()){
                try{bloqueoInstancia=asegurarInstanciaUnica(ds,bloqueoInstancia);controlador.ejecutarCiclo();fallosConsecutivos=0;dormir(activo,cfg.pollSeconds());}
                catch(InterruptedException ex){Thread.currentThread().interrupt();break;}
                catch(Exception ex){BitacoraArchivos.sistema().error("REINTENTO_SERVICIO","-",ex);if(bloqueoInstancia!=null&&!valida(bloqueoInstancia)){try{bloqueoInstancia.close();}catch(SQLException cierre){ex.addSuppressed(cierre);}bloqueoInstancia=null;}fallosConsecutivos++;long espera=esperaReintento(fallosConsecutivos,cfg.pollSeconds(),cfg.retryMaxSeconds());System.err.println("Servicio temporalmente no disponible. Reintento "+fallosConsecutivos+" en "+espera+" s: "+seguro(ex));dormir(activo,espera);}
            }
            if(bloqueoInstancia!=null)bloqueoInstancia.close();
        }
        System.out.println("Servicio detenido de forma segura.");
        BitacoraArchivos.sistema().acceso("DETENCION", "-", "-", "SERVICIO", true);
    }

    static long esperaReintento(int fallos,int base,int max){long exp=Math.min(max,Math.max(base,1L<<Math.min(fallos,8)));long jitter=ThreadLocalRandom.current().nextLong(Math.max(1,exp/4+1));return Math.min(max,exp+jitter);}
    private static Connection asegurarInstanciaUnica(HikariDataSource ds, Connection actual) throws SQLException {
        if (valida(actual)) return actual;
        if (actual != null) actual.close();

        Connection conexion = ds.getConnection();
        try (var consulta = conexion.prepareStatement("select pg_try_advisory_lock(?)")) {
            consulta.setLong(1, 5130909L);
            try (var resultado = consulta.executeQuery()) {
                if (!resultado.next() || !resultado.getBoolean(1)) {
                    throw new SQLException("Ya existe otra instancia activa del servicio");
                }
            }
            return conexion;
        } catch (SQLException | RuntimeException fallo) {
            // El bloqueo vive en la sesión. Retira la conexión si el estado quedó incierto.
            try {
                ds.evictConnection(conexion);
            } catch (RuntimeException cierre) {
                fallo.addSuppressed(cierre);
            }
            try {
                conexion.close();
            } catch (SQLException | RuntimeException cierre) {
                fallo.addSuppressed(cierre);
            }
            throw fallo;
        }
    }
    private static boolean valida(Connection c){try{return c!=null&&!c.isClosed()&&c.isValid(3);}catch(SQLException ex){return false;}}
    private static void dormir(AtomicBoolean activo,long segundos)throws InterruptedException{if(activo.get())Thread.sleep(Math.max(1,segundos)*1000L);}
    private static String seguro(Throwable ex){String m=ex.getMessage();if(m==null)m=ex.getClass().getSimpleName();return limitar(m.replaceAll("(?i)(password|contraseña)\\s*[=:]\\s*\\S+","$1=[OCULTO]"),500);}
    private static String limitar(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n);}
}
