package bo.edu.uagrm.inf513.grupo09sa.presentacion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControladorCorreoTest {
    @Test void validaRemitentesAntesDeResponder(){assertTrue(ControladorCorreo.correoSeguro("persona@example.com"));assertFalse(ControladorCorreo.correoSeguro("victima@example.com\r\nBcc:otro@example.com"));assertFalse(ControladorCorreo.correoSeguro("a..b@example.com"));assertFalse(ControladorCorreo.correoSeguro(null));}
    @Test void noConvierteUnAsuntoExcesivoEnOrdenValida(){String ataque="AYUDA[]"+"X".repeat(1000);assertEquals("RECHAZADO: ASUNTO DEMASIADO LARGO",ControladorCorreo.normalizarAsunto(ataque));}
    @Test void rechazaIdentificadoresQueContaminanRegistros(){assertTrue(ControladorCorreo.idSeguro("UID-123.abc"));assertFalse(ControladorCorreo.idSeguro("UID\r\nFALSO"));assertFalse(ControladorCorreo.idSeguro("X".repeat(256)));}
}
