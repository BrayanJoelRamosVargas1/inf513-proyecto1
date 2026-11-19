package bo.edu.uagrm.inf513.grupo09sa.presentacion;

import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Operaciones que necesita el ciclo de correo, independientes del motor de datos. */
public interface ColaCorreo {
    enum Admision { DIFERIDO, DUPLICADO, SATURADO }

    record CorreoControl(String id, String remitente, String asunto, String estado,
                         String respuestaAsunto, String respuestaCuerpo, Boolean exito, int intentos) {}

    boolean servicioInicializado(String protocolo) throws Exception;
    void marcarServicioInicializado() throws Exception;
    void marcarServicioInicializado(String protocolo) throws Exception;
    void registrarCorreoProcesado(String id, String remitente, String asunto, boolean exito) throws Exception;
    Set<String> correosConocidos(Collection<String> candidatos) throws Exception;
    boolean autorizado(String correo, String rol) throws Exception;
    Admision registrarEntrada(String id, String remitente, String asunto, List<AdjuntoPdf> adjuntos) throws Exception;
    int promoverDiferidos(int limite) throws Exception;
    List<CorreoControl> correosPendientes(int limite) throws Exception;
    void guardarRespuesta(String id, Resultado resultado) throws Exception;
    void confirmarRespuesta(String id) throws Exception;
    void registrarFallo(String id, String error, int maxSegundos) throws Exception;
}
