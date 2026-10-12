package bo.edu.uagrm.inf513.grupo09sa;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
public final class AplicarEsquema {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();String sql=Files.readString(Path.of("database","schema.sql"));
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var s=c.createStatement()){
            c.setAutoCommit(false);try{for(String bloque:sql.split(";")){if(!bloque.isBlank())s.execute(bloque);}c.commit();System.out.println("SCHEMA_OK");}catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
