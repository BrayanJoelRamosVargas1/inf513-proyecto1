package bo.edu.uagrm.inf513.grupo09sa;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.DriverManager;
public final class ImportarBaseline {
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();int count=0;
        try(var c=DriverManager.getConnection(cfg.dbUrl(),cfg.dbUser(),cfg.dbPassword());var in=new BufferedReader(new InputStreamReader(System.in));var p=c.prepareStatement("insert into p1_correo_procesado(mensaje_id,remitente,asunto,exito) values(?,'historico@local','BASELINE',true) on conflict(mensaje_id) do nothing")){c.setAutoCommit(false);String id;while((id=in.readLine())!=null){id=id.trim();if(!id.isEmpty()){p.setString(1,id);p.addBatch();count++;}}p.executeBatch();try(var state=c.prepareStatement("insert into p1_servicio_estado(clave,valor) values('bandeja_inicializada','true') on conflict(clave) do update set valor=excluded.valor,actualizado_en=now()")){state.executeUpdate();}c.commit();}
        System.out.println("BASELINE_OK count="+count);
    }
}
