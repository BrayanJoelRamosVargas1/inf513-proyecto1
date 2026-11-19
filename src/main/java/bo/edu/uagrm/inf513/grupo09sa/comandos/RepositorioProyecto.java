package bo.edu.uagrm.inf513.grupo09sa.comandos;

public interface RepositorioProyecto {
    boolean autorizado(String correo,String rol) throws Exception;
    boolean hayUsuarios() throws Exception;
    Resultado ejecutar(String remitente, Orden orden) throws Exception;
    default Resultado ejecutarIdempotente(String mensajeId,String remitente,Orden orden)throws Exception{return ejecutar(remitente,orden);}
    default Resultado solicitarConfirmacion(String mensajeId,String remitente,Orden orden)throws Exception{throw new IllegalStateException("Repositorio sin soporte de confirmación segura");}
    @FunctionalInterface interface OrdenConfirmada { Resultado ejecutar(String mensajeOriginal,Orden orden)throws Exception; }
    default Resultado confirmarOrden(String mensajeId,String remitente,String token,OrdenConfirmada ejecutar)throws Exception{throw new IllegalStateException("Repositorio sin soporte de confirmación segura");}
}
