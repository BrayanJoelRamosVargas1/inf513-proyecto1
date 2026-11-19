package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** Verificación de un requisito por el evaluador asignado. */
public record EvaluacionRequisito(long postulacionId, String requisito, boolean cumple,
                                  String observacion, String evaluadorCorreo) {
}
