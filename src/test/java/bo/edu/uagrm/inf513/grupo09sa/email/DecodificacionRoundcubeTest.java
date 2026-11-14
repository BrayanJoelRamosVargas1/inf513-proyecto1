package bo.edu.uagrm.inf513.grupo09sa.email;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecodificacionRoundcubeTest {
    @Test void decodificaComillasYUtf8DelListadoJavascript() {
        String fragmento="INSPER[\\\"1\\\",\\\"Pe\\u00f1a\\\"]";
        assertEquals("INSPER[\"1\",\"Peña\"]",ClienteRoundcube.html(fragmento));
    }
    @Test void conservaLasEntidadesDeAsuntosQueYaLleganComoHtml() {
        assertEquals("LISPER[\"*\"]",ClienteRoundcube.html("LISPER[&quot;*&quot;]"));
    }
}
