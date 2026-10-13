package bo.edu.uagrm.inf513.grupo09sa.email;
import java.util.List;
import java.util.Collection;
import java.util.Set;
public interface PasarelaCorreo extends AutoCloseable {
    @FunctionalInterface interface ConsultaProcesados { Set<String> conocidos(Collection<String> ids) throws Exception; }
    List<String> identificadores() throws Exception;
    List<MensajeEntrada> noProcesados(ConsultaProcesados consulta) throws Exception;
    void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception;
    void marcarProcesado(MensajeEntrada mensaje)throws Exception;
    /** Libera objetos del lote; las órdenes y respuestas permanecen en PostgreSQL. */
    default void liberarReferencias(){}
}
