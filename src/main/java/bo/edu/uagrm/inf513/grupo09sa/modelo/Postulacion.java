package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** Solicitud de un postulante para un cargo de una convocatoria. */
public record Postulacion(long convocatoriaId, long cargoId, String correoPostulante) {
}
