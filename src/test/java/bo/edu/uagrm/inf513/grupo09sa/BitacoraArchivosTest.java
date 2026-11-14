package bo.edu.uagrm.inf513.grupo09sa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class BitacoraArchivosTest {
    @TempDir Path carpeta;
    @Test void creaAmbosArchivosYSoloRegistraMetadatos() throws Exception {
        var log = new BitacoraArchivos(carpeta);
        log.acceso("PROCESADO", "m1\nFALSO", "demo@example.com", "INSPOST", true);
        log.error("BASE_DATOS", "m1", new SQLException("password=secreto CONFIRMAR[\"codigo\"]", "23505"));
        String acceso = Files.readString(carpeta.resolve("access.log"));
        String error = Files.readString(carpeta.resolve("error.log"));
        assertEquals(1, acceso.lines().count());
        assertTrue(acceso.contains("m1_FALSO"));
        assertTrue(error.contains("sqlstate=23505"));
        assertFalse(error.contains("secreto"));
        assertFalse(error.contains("codigo"));
    }
    @Test void rotaSinPerderElNombreExigido() throws Exception {
        var log = new BitacoraArchivos(carpeta, 180);
        for (int i=0; i<30; i++) log.acceso("RECIBIDO", "m"+i, "demo@example.com", "AYUDA", true);
        assertTrue(Files.size(carpeta.resolve("access.log")) > 0);
        assertTrue(Files.exists(carpeta.resolve("access.log.1")));
        assertTrue(Files.exists(carpeta.resolve("access.log.3")));
        assertFalse(Files.exists(carpeta.resolve("access.log.4")));
    }
}
