package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** Prioridad justificada para una postulación empatada. */
public record Desempate(long postulacionId, int prioridad, String justificacion, String actor) {
}
