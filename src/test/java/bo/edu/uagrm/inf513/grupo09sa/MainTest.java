package bo.edu.uagrm.inf513.grupo09sa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test void reintentoNuncaSuperaElMaximo(){for(int i=1;i<30;i++){long n=Main.esperaReintento(i,10,300);assertTrue(n>=10&&n<=300);}}
    @Test void reintentoCreceTrasFallos(){long primero=Main.esperaReintento(1,10,300);long tardio=Main.esperaReintento(8,10,300);assertTrue(tardio>primero);}
}
