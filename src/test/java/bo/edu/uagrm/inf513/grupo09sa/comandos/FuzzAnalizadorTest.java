package bo.edu.uagrm.inf513.grupo09sa.comandos;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.fail;

class FuzzAnalizadorTest {
    @Test void entradasAleatoriasNoDerribanElAnalizador(){probar(Integer.getInteger("fuzz.cases",50_000),51309L);}

    static void probar(int cantidad,long semilla){
        AnalizadorOrden analizador=new AnalizadorOrden();Random random=new Random(semilla);
        String alfabeto="ABCxyz019_[],\\\" *@.-áéíóú\n\r\t💻";
        for(int caso=0;caso<cantidad;caso++){
            int longitud=random.nextInt(1300);StringBuilder s=new StringBuilder(longitud);
            for(int i=0;i<longitud;i++)s.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
            try{analizador.analizar(s.toString());}
            catch(OrdenInvalidaException esperado){}
            catch(Throwable inesperado){fail("Fallo inesperado en caso "+caso,inesperado);}
        }
    }
}
