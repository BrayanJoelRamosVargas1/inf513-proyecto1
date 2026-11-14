package bo.edu.uagrm.inf513.grupo09sa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Local event log. Subjects, parameters and confirmation codes are never logged. */
public final class BitacoraArchivos {
    private final Path directorio;
    private final long limite;
    private static final class Instancia {
        static final BitacoraArchivos VALOR = new BitacoraArchivos(Path.of(
                System.getProperty("app.log.dir", System.getenv().getOrDefault("LOG_DIR", "logs"))));
    }
    public static BitacoraArchivos sistema() { return Instancia.VALOR; }
    public BitacoraArchivos(Path directorio) { this(directorio, 5L * 1024 * 1024); }
    public BitacoraArchivos(Path directorio, long limite) {
        if (limite < 100) throw new IllegalArgumentException("Límite de rotación demasiado pequeño");
        this.directorio = directorio;
        this.limite = limite;
        try {
            Files.createDirectories(directorio);
            for (String nombre : new String[]{"access.log", "error.log"}) {
                try (var ignored = Files.newOutputStream(directorio.resolve(nombre),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) { }
            }
        } catch (IOException ex) { throw new UncheckedIOException("No se puede crear la bitácora local", ex); }
    }
    public void acceso(String evento, String id, String remitente, String comando, boolean exito) {
        escribir("access.log", evento + " id=" + limpiar(id) + " remitente=" + limpiar(remitente)
                + " comando=" + limpiar(comando) + " exito=" + exito);
    }
    public void error(String evento, String id, Throwable fallo) {
        String tipo = fallo == null ? "Validacion" : fallo.getClass().getSimpleName();
        // Exception messages can contain credentials or SQL values. Record types and SQLSTATE only.
        String estado = "";
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof java.sql.SQLException sql) { estado = " sqlstate=" + limpiar(sql.getSQLState()); break; }
        }
        escribir("error.log", limpiar(evento) + " id=" + limpiar(id) + " tipo=" + tipo + estado);
    }
    private synchronized void escribir(String archivo, String texto) {
        Path destino = directorio.resolve(archivo);
        try {
            byte[] linea = (Instant.now() + " " + texto + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            if (Files.size(destino) + linea.length > limite) {
                for (int i = 2; i >= 1; i--) {
                    Path previo = directorio.resolve(archivo + "." + i);
                    if (Files.exists(previo)) Files.move(previo, directorio.resolve(archivo + "." + (i + 1)), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(destino, directorio.resolve(archivo + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(destino, linea, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) { System.err.println("No se pudo escribir " + archivo + ": " + ex.getClass().getSimpleName()); }
    }
    private static String limpiar(String dato) {
        if (dato == null) return "-";
        return dato.replaceAll("[\\p{Cntrl}\\s]+", "_").substring(0, Math.min(255, dato.replaceAll("[\\p{Cntrl}\\s]+", "_").length()));
    }
}
