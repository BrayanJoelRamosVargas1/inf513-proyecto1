package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.*;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Main {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();HikariConfig hc=new HikariConfig();hc.setJdbcUrl(cfg.dbUrl());hc.setUsername(cfg.dbUser());hc.setPassword(cfg.dbPassword());hc.setMaximumPoolSize(4);hc.setMinimumIdle(1);
        try(HikariDataSource ds=new HikariDataSource(hc);PasarelaCorreo mail=new PasarelaJakartaMail(cfg)){
            AnalizadorOrden parser=new AnalizadorOrden();RepositorioPostgres repo=new RepositorioPostgres(ds);ProcesadorOrden processor=new ProcesadorOrden(new CatalogoComandos(),repo);
            System.out.println("INF513 Grupo 09 SA - servicio de correo iniciado. Intervalo: "+cfg.pollSeconds()+" s");
            while(!Thread.currentThread().isInterrupted()){
                try{if(!repo.servicioInicializado()){var ids=mail.identificadores();for(String id:ids)repo.registrarCorreoProcesado(id,"historico@local","BASELINE",true);repo.marcarServicioInicializado();System.out.println("Bandeja inicializada: "+ids.size()+" mensajes históricos omitidos.");Thread.sleep(cfg.pollSeconds()*1000L);continue;}var procesados=repo.correosProcesados();for(MensajeEntrada msg:mail.noProcesados(procesados::contains)){Resultado r;try{r=processor.procesar(msg.remitente(),parser.analizar(msg.asunto()));}catch(OrdenInvalidaException ex){r=Resultado.error(ex.getMessage());}mail.responder(msg,r.asunto(),r.cuerpo());repo.registrarCorreoProcesado(msg.id(),msg.remitente(),msg.asunto(),r.exito());mail.marcarProcesado(msg);System.out.println("Procesado correo "+msg.id()+" de "+msg.remitente()+": "+r.exito());}}
                catch(Exception ex){System.err.println("Error durante el ciclo: "+ex.getMessage());}
                Thread.sleep(cfg.pollSeconds()*1000L);
            }
        }
    }
}
