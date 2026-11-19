package bo.edu.uagrm.inf513.grupo09sa.modelo;

import java.math.BigDecimal;

/** Área y ponderación de una oferta. */
public record AreaExamen(long convocatoriaId, long cargoId, String area, BigDecimal peso) {
}
