package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class AplicarEsquema {
    private AplicarEsquema() {}

    public static void main(String[] args) throws Exception {
        AppConfig cfg = AppConfig.load();
        String sql = Files.readString(Path.of("database", "schema.sql"));
        try (var conexion = DriverManager.getConnection(cfg.dbUrl(), cfg.dbUser(), cfg.dbPassword());
             var consulta = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                // JDBC conserva los cuerpos PL/pgSQL y el script se aplica como una transacción.
                consulta.execute(sql);
                conexion.commit();
                System.out.println("SCHEMA_OK");
            } catch (SQLException fallo) {
                try {
                    conexion.rollback();
                } catch (SQLException reversion) {
                    fallo.addSuppressed(reversion);
                }
                throw fallo;
            }
        }
    }
}
