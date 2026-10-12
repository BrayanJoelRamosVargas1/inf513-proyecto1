package bo.edu.uagrm.inf513.grupo09sa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test void validaRemitentesAntesDeResponder(){assertTrue(Main.correoSeguro("persona@example.com"));assertFalse(Main.correoSeguro("victima@example.com\r\nBcc:otro@example.com"));assertFalse(Main.correoSeguro("a..b@example.com"));assertFalse(Main.correoSeguro(null));}
    @Test void reintentoNuncaSuperaElMaximo(){for(int i=1;i<30;i++){long n=Main.esperaReintento(i,10,300);assertTrue(n>=10&&n<=300);}}
    @Test void reintentoCreceTrasFallos(){long primero=Main.esperaReintento(1,10,300);long tardio=Main.esperaReintento(8,10,300);assertTrue(tardio>primero);}
    @Test void noConvierteUnAsuntoExcesivoEnOrdenValida(){String ataque="AYUDA[]"+"X".repeat(1000);assertEquals("RECHAZADO: ASUNTO DEMASIADO LARGO",Main.normalizarAsunto(ataque));}
    @Test void rechazaIdentificadoresQueContaminanRegistros(){assertTrue(Main.idSeguro("UID-123.abc"));assertFalse(Main.idSeguro("UID\r\nFALSO"));assertFalse(Main.idSeguro("X".repeat(256)));}
}
