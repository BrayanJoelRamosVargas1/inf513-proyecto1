package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.sql.DriverManager;

/** Comprueba la presencia de barreras críticas sin mostrar datos ni secretos. */
public final class DiagnosticoIntegridad {
    private DiagnosticoIntegridad(){}
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();
        String sql="select count(*) from pg_constraint where conname in " +
                "('p1_convocatoria_fechas_ck','p1_merito_puntaje_ck','p1_examen_puntaje_ck','p1_postulacion_puntajes_ck') and contype='c' and convalidated=true";
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var s=c.createStatement();var r=s.executeQuery(sql)){
            if(!r.next()||r.getInt(1)!=4)throw new IllegalStateException("Faltan restricciones críticas");
            System.out.println("INTEGRIDAD_OK restricciones=4");
        }
    }
}
