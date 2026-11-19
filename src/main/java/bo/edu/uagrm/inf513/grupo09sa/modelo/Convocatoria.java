package bo.edu.uagrm.inf513.grupo09sa.modelo;

import java.time.LocalDate;

/** Convocatoria con su vigencia, estado y cupo total. */
public record Convocatoria(String codigo, String titulo, LocalDate fechaInicio, LocalDate fechaFin,
                           String estado, int plazas) {
}
