package bo.edu.uagrm.inf513.grupo09sa.persistencia;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Orden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import java.util.Set;
public interface RepositorioProyecto {
    boolean autorizado(String correo,String rol) throws Exception;
    boolean hayUsuarios() throws Exception;
    boolean correoProcesado(String id) throws Exception;
    Set<String> correosProcesados() throws Exception;
    boolean servicioInicializado() throws Exception;
    void marcarServicioInicializado() throws Exception;
    void registrarCorreoProcesado(String id,String remitente,String asunto,boolean exito) throws Exception;
    Resultado ejecutar(String remitente, Orden orden) throws Exception;
    default Resultado ejecutarIdempotente(String mensajeId,String remitente,Orden orden)throws Exception{return ejecutar(remitente,orden);}
}
