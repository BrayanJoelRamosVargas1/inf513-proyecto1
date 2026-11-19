package bo.edu.uagrm.inf513.grupo09sa.modelo;

import java.math.BigDecimal;

/** Nota de examen general o por área para una postulación. */
public record Examen(long postulacionId, String area, BigDecimal puntaje, String observacion,
                     String evaluadorCorreo) {
}
