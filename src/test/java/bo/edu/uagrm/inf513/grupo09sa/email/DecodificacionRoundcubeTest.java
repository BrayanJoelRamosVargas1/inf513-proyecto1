package bo.edu.uagrm.inf513.grupo09sa.email;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecodificacionRoundcubeTest {
    @Test void identificaUnPdfSubidoYRechazaUnaCargaFallida(){
        assertEquals("abc_123",ClienteRoundcube.identificadorAdjunto("parent.rcmail.add2attachment_list(\"rcmfileabc_123\",{\"name\":\"expediente.pdf\"});"));
        assertEquals("123",ClienteRoundcube.identificadorAdjunto("parent.rcmail.command('add2attachment_list',\"rcmfile123\",{});"));
        assertNull(ClienteRoundcube.identificadorAdjunto("display_message('fileuploaderror','error')"));
    }
    @Test void decodificaComillasYUtf8DelListadoJavascript() {
        String fragmento="INSPER[\\\"1\\\",\\\"Pe\\u00f1a\\\"]";
        assertEquals("INSPER[\"1\",\"Peña\"]",ClienteRoundcube.html(fragmento));
    }
    @Test void conservaLasEntidadesDeAsuntosQueYaLleganComoHtml() {
        assertEquals("LISPER[\"*\"]",ClienteRoundcube.html("LISPER[&quot;*&quot;]"));
    }
}
