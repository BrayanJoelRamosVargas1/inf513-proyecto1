package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaJakartaMail;
import java.sql.DriverManager;

/** Diagnóstico de autenticación sin modificar mensajes ni tablas. */
public final class InfraestructuraProbe {
    public static void main(String[] args) throws Exception {
        AppConfig cfg=AppConfig.load();
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var s=c.createStatement();var r=s.executeQuery("select current_database(), current_user")){
            r.next(); System.out.println("DB_OK database="+r.getString(1)+" user="+r.getString(2));
        }
        try(var mail=new PasarelaJakartaMail(cfg)){
            System.out.println("MAIL_OK pending="+mail.noProcesados(id->false).size());
        }
    }
}
