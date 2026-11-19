package bo.edu.uagrm.inf513.grupo09sa.modelo;

/** Cuenta del sistema con un rol y un estado de activación. */
public record Usuario(String registro, String nombres, String apellidos, String rol,
                      String telefono, String correo, boolean activo) {
}
