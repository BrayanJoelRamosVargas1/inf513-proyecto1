package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** Datos del registro de personas solicitado en el ejemplo docente. */
public record Persona(String ci, String nombres, String apellidos, String ocupacion,
                      String telefono, String celular, String correo) {
}
