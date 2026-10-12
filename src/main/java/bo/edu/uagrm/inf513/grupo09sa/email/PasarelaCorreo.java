package bo.edu.uagrm.inf513.grupo09sa.email;
import java.util.List;
import java.util.function.Predicate;
public interface PasarelaCorreo extends AutoCloseable { List<String> identificadores() throws Exception; List<MensajeEntrada> noProcesados(Predicate<String> yaProcesado) throws Exception; void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception; void marcarProcesado(MensajeEntrada mensaje)throws Exception; }
