package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** PDF o referencia externa del expediente de una postulación. */
public record Expediente(long postulacionId, String rutaArchivo, String nombreArchivo,
                         byte[] contenido, String sha256) {
}
