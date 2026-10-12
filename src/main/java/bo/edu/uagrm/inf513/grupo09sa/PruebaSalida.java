package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.MensajeEntrada;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaJakartaMail;

/** Prueba manual: envía un único mensaje al destinatario indicado. */
public final class PruebaSalida {
    private PruebaSalida() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Uso: PruebaSalida destinatario asunto");
        AppConfig cfg=AppConfig.load();
        try(PasarelaJakartaMail mail=new PasarelaJakartaMail(cfg)){
            mail.responder(new MensajeEntrada("prueba",args[0],args[1]),args[1],"Prueba controlada del sistema INF513 Grupo 09 SA.");
        }
        System.out.println("ENVIO_CONFIRMADO_POR_ROUNDCUBE");
    }
}
