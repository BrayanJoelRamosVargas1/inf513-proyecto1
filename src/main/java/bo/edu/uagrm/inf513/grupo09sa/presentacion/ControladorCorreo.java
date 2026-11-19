package bo.edu.uagrm.inf513.grupo09sa.presentacion;

import bo.edu.uagrm.inf513.grupo09sa.BitacoraArchivos;
import bo.edu.uagrm.inf513.grupo09sa.comandos.AnalizadorOrden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.OrdenInvalidaException;
import bo.edu.uagrm.inf513.grupo09sa.comandos.ProcesadorOrden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import bo.edu.uagrm.inf513.grupo09sa.email.InspectorPdf;
import bo.edu.uagrm.inf513.grupo09sa.email.MensajeEntrada;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaCorreo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Recibe órdenes, invoca la aplicación y entrega sus respuestas por correo. */
public final class ControladorCorreo {
    private static final Pattern CORREO_SEGURO=Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9.-]{1,253}$");

    private final String protocolo;
    private final int maxMensajes;
    private final int maxReintento;
    private final PasarelaCorreo mail;
    private final ColaCorreo cola;
    private final ProcesadorOrden procesador;
    private final AnalizadorOrden analizador;
    private final InspectorPdf inspector;
    private AvisosConsulta avisos;

    public ControladorCorreo(String protocolo, int maxMensajes, int maxReintento,
            PasarelaCorreo mail, ColaCorreo cola, ProcesadorOrden procesador,
            AnalizadorOrden analizador, InspectorPdf inspector) {
        this.protocolo = protocolo;
        this.maxMensajes = maxMensajes;
        this.maxReintento = maxReintento;
        this.mail = mail;
        this.cola = cola;
        this.procesador = procesador;
        this.analizador = analizador;
        this.inspector = inspector;
    }

    public void habilitarAvisosConsulta(Path archivo) throws IOException {
        if (protocolo.equals("pop3")) avisos = new AvisosConsulta(archivo);
    }

    public void responderConsultasSinPostgres() throws Exception {
        if (avisos != null) avisos.responder(mail, analizador);
    }

    public void ejecutarCiclo() throws Exception {
        if(!cola.servicioInicializado(protocolo)){
            var ids=mail.identificadores();for(String id:ids)cola.registrarCorreoProcesado(id,"historico@local","BASELINE",true);
            cola.marcarServicioInicializado(protocolo);
            if (avisos != null) avisos.observar(new HashSet<>(ids));
            System.out.println("Bandeja inicializada: "+ids.size()+" mensajes históricos omitidos.");return;
        }
        if (avisos != null) avisos.reconciliar(cola);
        try{for(MensajeEntrada recibido:recibir()){
            MensajeEntrada msg=recibido;
            if(!idSeguro(msg.id())){
                BitacoraArchivos.sistema().error("ID_INVALIDO","-",null);
                System.err.println("Correo descartado por identificador inválido.");continue;
            }
            String asunto=normalizarAsunto(msg.asunto());
            if(!correoSeguro(msg.remitente())){cola.registrarCorreoProcesado(msg.id(),"invalido@local",asunto,false);BitacoraArchivos.sistema().error("REMITENTE_INVALIDO",msg.id(),null);System.err.println("Correo descartado por remitente inválido.");continue;}
            if(esRespuestaDelServicio(asunto)){
                cola.registrarCorreoProcesado(msg.id(),msg.remitente(),asunto,false);
                mail.marcarProcesado(msg);continue;
            }
            try{
                if(!msg.adjuntos().isEmpty()){
                    if(!cola.autorizado(msg.remitente(),"POSTULANTE"))msg=new MensajeEntrada(msg.id(),msg.remitente(),asunto);
                    else {
                        String rechazo=null;
                        try {for(var pdf:msg.adjuntos())if(!inspector.aprobado(pdf)){rechazo="El antivirus no aprobó el PDF. No se almacenó; revise el archivo y envíe una nueva orden.";break;}}
                        catch(java.io.IOException falloAnalisis){rechazo="El análisis antivirus no estuvo disponible. No se almacenó el PDF; consulte al operador y envíe una nueva orden.";}
                        if(rechazo!=null){asunto="ADJUNTO_RECHAZADO: "+rechazo;msg=new MensajeEntrada(msg.id(),msg.remitente(),asunto);}
                    }
                }
                if(cola.registrarEntrada(msg.id(),msg.remitente(),asunto,msg.adjuntos())!=ColaCorreo.Admision.SATURADO){BitacoraArchivos.sistema().acceso("RECIBIDO",msg.id(),msg.remitente(),comandoSeguro(asunto),true);mail.marcarProcesado(msg);}
            }
            catch(InterruptedException interrumpido){throw interrumpido;}
            catch(Exception ex){BitacoraArchivos.sistema().error("RECEPCION",msg.id(),ex);System.err.println("No se pudo registrar un correo; los demás continuarán: "+seguro(ex));}
        }}finally{mail.liberarReferencias();}
        cola.promoverDiferidos(maxMensajes);
        for(var pendiente:cola.correosPendientes(maxMensajes)){
            try{
                if(esRespuestaDelServicio(pendiente.asunto())){cola.confirmarRespuesta(pendiente.id());continue;}
                if("RECIBIDO".equals(pendiente.estado())){
                    Resultado resultado;
                    if(pendiente.asunto()!=null&&pendiente.asunto().startsWith("ADJUNTO_RECHAZADO: "))resultado=Resultado.error(pendiente.asunto().substring("ADJUNTO_RECHAZADO: ".length()));
                    else try{resultado=procesador.procesar(pendiente.id(),pendiente.remitente(),analizador.analizar(pendiente.asunto()));}
                    catch(OrdenInvalidaException ex){
                        String prefijo = comandoSeguro(pendiente.asunto()).equals("INSPER") ? "Error: " : "";
                        resultado = Resultado.error(prefijo + ex.getMessage());
                    }
                    BitacoraArchivos.sistema().acceso("PROCESADO",pendiente.id(),pendiente.remitente(),comandoSeguro(pendiente.asunto()),resultado.exito());
                    if(!resultado.exito()&&!resultado.asunto().startsWith("CONFIRMACIÓN REQUERIDA"))BitacoraArchivos.sistema().error("ORDEN_RECHAZADA",pendiente.id(),null);
                    cola.guardarRespuesta(pendiente.id(),resultado);continue;
                }
                MensajeEntrada original=new MensajeEntrada(pendiente.id(),pendiente.remitente(),pendiente.asunto());
                mail.responder(original,pendiente.respuestaAsunto(),pendiente.respuestaCuerpo());
                cola.confirmarRespuesta(pendiente.id());
                try {
                    mail.marcarProcesado(original);
                } catch (InterruptedException interrumpido) {
                    throw interrumpido;
                } catch (Exception falloMarcado) {
                    BitacoraArchivos.sistema().error("MARCADO_POSTERIOR",pendiente.id(),falloMarcado);
                }
                System.out.println("Respondido correo "+pendiente.id()+" de "+pendiente.remitente()+". Éxito: "+pendiente.exito());
                BitacoraArchivos.sistema().acceso("RESPONDIDO",pendiente.id(),pendiente.remitente(),comandoSeguro(pendiente.asunto()),Boolean.TRUE.equals(pendiente.exito()));
            } catch (InterruptedException interrumpido) {
                throw interrumpido;
            } catch (Exception ex) {
                BitacoraArchivos.sistema().error("CORREO_PENDIENTE",pendiente.id(),ex);
                try{cola.registrarFallo(pendiente.id(),seguro(ex),maxReintento);}catch(Exception registro){System.err.println("No se pudo persistir el fallo: "+seguro(registro));}
                System.err.println("Correo "+pendiente.id()+" pendiente de reintento: "+seguro(ex));
            }
        }
    }
    private List<MensajeEntrada> recibir() throws Exception {
        if (avisos == null) return mail.noProcesados(cola::correosConocidos);
        Set<String> idsObservados = new HashSet<>();
        var mensajes = mail.noProcesados(ids -> {
            idsObservados.addAll(ids);
            return cola.correosConocidos(ids);
        });
        avisos.observar(idsObservados);
        return mensajes;
    }
    static String comandoSeguro(String asunto){if(asunto==null)return "INVALIDO";var m=Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_]{0,30})\\s*\\[").matcher(asunto);return m.find()?m.group(1).toUpperCase(java.util.Locale.ROOT):"INVALIDO";}
    static boolean idSeguro(String id){return id!=null&&!id.isBlank()&&id.length()<=255&&id.chars().noneMatch(Character::isISOControl);}
    static boolean esRespuestaDelServicio(String asunto){return "ÉXITO - Orden procesada".equals(asunto)||"ERROR - Orden no procesada".equals(asunto)||(asunto!=null&&asunto.startsWith("CONFIRMACIÓN REQUERIDA - "));}
    static String normalizarAsunto(String asunto){if(asunto==null)return "";return asunto.length()>998?"RECHAZADO: ASUNTO DEMASIADO LARGO":asunto;}
    static boolean correoSeguro(String correo){return correo!=null&&correo.length()<=160&&!correo.contains("..")&&CORREO_SEGURO.matcher(correo).matches();}
    private static String seguro(Throwable ex){String m=ex.getMessage();if(m==null)m=ex.getClass().getSimpleName();return limitar(m.replaceAll("(?i)(password|contraseña)\\s*[=:]\\s*\\S+","$1=[OCULTO]"),500);}
    private static String limitar(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,n);}
}
