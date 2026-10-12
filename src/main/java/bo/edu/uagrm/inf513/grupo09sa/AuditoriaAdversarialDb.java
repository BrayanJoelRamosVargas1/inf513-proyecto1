package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.PreparedStatement;
import java.util.UUID;

/** Pruebas negativas contra PostgreSQL. Toda la sesión se revierte al finalizar. */
public final class AuditoriaAdversarialDb {
    private AuditoriaAdversarialDb(){}
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();String suf=UUID.randomUUID().toString().replace("-","").substring(0,12);int rechazadas=0;
        try(Connection c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword())){
            c.setAutoCommit(false);
            try{
                rechazadas+=debeRechazarConvocatoria(c,"X"+suf,"2026-10-20","2026-10-01","ABIERTA");
                rechazadas+=debeRechazarConvocatoria(c,"Y"+suf,"2026-10-01","2026-10-20","INVENTADO");
                insertarUsuario(c,suf,"audit"+suf+"@example.com");
                rechazadas+=debeRechazarUsuario(c,"Z"+suf,"AUDIT"+suf+"@EXAMPLE.COM");
                if(rechazadas!=3)throw new IllegalStateException("Una barrera crítica aceptó datos inválidos");
                System.out.println("AUDITORIA_DB_OK rechazos="+rechazadas+" rollback=true");
            }finally{c.rollback();}
        }
    }
    private static int debeRechazarConvocatoria(Connection c,String codigo,String inicio,String fin,String estado)throws SQLException{
        Savepoint p=c.setSavepoint();try(var s=c.prepareStatement("insert into p1_convocatoria(codigo,titulo,fecha_inicio,fecha_fin,estado,plazas) values(?,'x',?::date,?::date,?,1)")){
            s.setString(1,codigo);s.setString(2,inicio);s.setString(3,fin);s.setString(4,estado);s.executeUpdate();c.rollback(p);throw new IllegalStateException("PostgreSQL aceptó una convocatoria inválida");
        }catch(SQLException esperado){c.rollback(p);String sqlState=esperado.getSQLState();if(!"23514".equals(sqlState)&&!"23505".equals(sqlState))throw esperado;return 1;}
    }
    private static void insertarUsuario(Connection c,String registro,String correo)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("insert into p1_usuario(registro,nombres,apellidos,rol,telefono,correo,activo) values(?,'Prueba','Rollback','POSTULANTE','0',?,true)")){s.setString(1,registro);s.setString(2,correo);s.executeUpdate();}
    }
    private static int debeRechazarUsuario(Connection c,String registro,String correo)throws SQLException{
        Savepoint p=c.setSavepoint();try{insertarUsuario(c,registro,correo);c.rollback(p);throw new IllegalStateException("PostgreSQL aceptó un correo duplicado");
        }catch(SQLException esperado){c.rollback(p);if(!"23505".equals(esperado.getSQLState()))throw esperado;return 1;}
    }
}
