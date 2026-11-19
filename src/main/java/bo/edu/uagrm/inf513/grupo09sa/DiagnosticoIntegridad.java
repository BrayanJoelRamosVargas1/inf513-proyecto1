package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.sql.DriverManager;

/** Comprueba la presencia de barreras críticas sin mostrar datos ni secretos. */
public final class DiagnosticoIntegridad {
    private DiagnosticoIntegridad(){}
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();
        String sql="select count(*) from pg_constraint where conname in " +
                "('convocatoria_fechas_ck','merito_puntaje_ck','examen_puntaje_ck','postulacion_puntajes_ck','convocatoria_estado_ck','postulacion_estado_ck','postulacion_plaza_ck') and connamespace=current_schema()::regnamespace and contype='c' and convalidated=true";
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var s=c.createStatement();var r=s.executeQuery(sql)){
            if(!r.next()||r.getInt(1)!=7)throw new IllegalStateException("Faltan restricciones críticas");
            try(var q=c.createStatement();var u=q.executeQuery("select count(*) from pg_indexes where schemaname=current_schema() and indexname='uq_usuario_correo_ci'")){if(!u.next()||u.getInt(1)!=1)throw new IllegalStateException("Falta unicidad de correo sin distinguir mayúsculas");}
            System.out.println("INTEGRIDAD_OK restricciones=7 indice_correo=1");
        }
    }
}
