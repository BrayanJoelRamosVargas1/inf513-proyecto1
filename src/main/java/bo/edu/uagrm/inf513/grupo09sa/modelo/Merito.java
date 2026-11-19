package bo.edu.uagrm.inf513.grupo09sa.modelo;

import java.math.BigDecimal;

/** Puntaje de un criterio de mérito para una postulación. */
public record Merito(long postulacionId, String criterio, BigDecimal puntaje, String observacion,
                     String evaluadorCorreo) {
}
