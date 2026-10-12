package bo.edu.uagrm.inf513.grupo09sa;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.sql.DriverManager;
public final class InspeccionarEsquema {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();String sql="select table_name,column_name,data_type from information_schema.columns where table_schema='public' order by table_name,ordinal_position";
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var s=c.createStatement();var r=s.executeQuery(sql)){
            while(r.next())System.out.println(r.getString(1)+" | "+r.getString(2)+" | "+r.getString(3));
        }
    }
}
