package bo.edu.uagrm.inf513.grupo09sa;
import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
public final class SmokeProyecto {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();HikariConfig hc=new HikariConfig();hc.setJdbcUrl(cfg.dbUrl());hc.setUsername(cfg.dbUser());hc.setPassword(cfg.dbPassword());
        try(HikariDataSource ds=new HikariDataSource(hc)){
            var parser=new AnalizadorOrden();var proc=new ProcesadorOrden(new CatalogoComandos(),new RepositorioPostgres(ds));
            Resultado r=proc.procesar("prueba@example.com",parser.analizar("LISCON[\"*\"]"));
            System.out.println("FLOW_OK="+r.exito());System.out.println(r.cuerpo());
        }
    }
}
