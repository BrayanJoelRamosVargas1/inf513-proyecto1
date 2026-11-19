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
        Path database = Path.of("database");
        String preparar = Files.readString(database.resolve("migrations/001_preparar.sql"));
        String instalar = Files.readString(database.resolve("schema.sql"));
        String historial = Files.readString(database.resolve("migrations/002_migrar_historial.sql"));
        String ofertas = Files.readString(database.resolve("migrations/003_postulacion_oferta.sql"));
        String evaluaciones = Files.readString(database.resolve("migrations/004_integridad_evaluacion.sql"));
        try (var conexion = DriverManager.getConnection(cfg.dbUrl(), cfg.dbUser(), cfg.dbPassword());
             var consulta = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                try (var bloqueo = consulta.executeQuery("select pg_try_advisory_xact_lock(5130909)")) {
                    bloqueo.next();
                    if (!bloqueo.getBoolean(1)) throw new SQLException("Detené el servicio antes de aplicar el esquema.");
                }
                consulta.execute("select set_config('search_path', quote_ident(current_schema()), true)");
                // PostgreSQL aplica el cambio y la recuperación de registros en una sola transacción.
                consulta.execute(preparar);
                consulta.execute(instalar);
                consulta.execute(historial);
                consulta.execute(ofertas);
                consulta.execute(evaluaciones);
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
