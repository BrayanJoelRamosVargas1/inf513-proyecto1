package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaCorreo;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaJakartaMail;

/** Comprueba autenticación y listado sin imprimir remitentes, asuntos ni secretos. */
public final class DiagnosticoSeguro {
    private DiagnosticoSeguro(){}
    public static void main(String[] args)throws Exception{
        AppConfig cfg=AppConfig.load();
        try(PasarelaCorreo correo=new PasarelaJakartaMail(cfg)){
            int cantidad=correo.identificadores().size();
            System.out.println("DIAGNOSTICO_OK transporte="+cfg.inboundProtocol()+" mensajes="+cantidad);
        }
    }
}
