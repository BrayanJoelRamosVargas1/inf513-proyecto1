package bo.edu.uagrm.inf513.grupo09sa;

/** Fallo temporal de red o servicio. Debe reintentarse y no responderse como error funcional. */
public final class InfraestructuraException extends RuntimeException {
    public InfraestructuraException(String mensaje,Throwable causa){super(mensaje,causa);}
}
