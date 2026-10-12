package bo.edu.uagrm.inf513.grupo09sa;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaJakartaMail;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
public final class InicializarBandeja {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();HikariConfig hc=new HikariConfig();hc.setJdbcUrl(cfg.dbUrl());hc.setUsername(cfg.dbUser());hc.setPassword(cfg.dbPassword());
        try(HikariDataSource ds=new HikariDataSource(hc);var mail=new PasarelaJakartaMail(cfg)){
            var repo=new RepositorioPostgres(ds);if(repo.servicioInicializado()){System.out.println("BASELINE_ALREADY_OK");return;}
            var ids=mail.identificadores();for(String id:ids)repo.registrarCorreoProcesado(id,"historico@local","BASELINE",true);repo.marcarServicioInicializado();System.out.println("BASELINE_OK count="+ids.size());
        }
    }
}
