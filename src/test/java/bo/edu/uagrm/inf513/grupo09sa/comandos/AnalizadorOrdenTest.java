package bo.edu.uagrm.inf513.grupo09sa.comandos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalizadorOrdenTest {
    private final AnalizadorOrden parser=new AnalizadorOrden();
    @Test void interpretaEjemploDelDocente()throws Exception{Orden o=parser.analizar("INSPER[\"4715292\",\"Juan Carlos\",\"Perez Seras\"]");assertEquals("INSPER",o.comando());assertEquals(3,o.parametros().size());assertEquals("Juan Carlos",o.parametros().get(1));}
    @Test void admiteComandoSinParametros()throws Exception{assertTrue(parser.analizar("AYUDA[]").parametros().isEmpty());}
    @Test void rechazaParametrosSinComillas(){assertThrows(OrdenInvalidaException.class,()->parser.analizar("LISPER[*]"));}
    @Test void rechazaAsuntoIncompleto(){assertThrows(OrdenInvalidaException.class,()->parser.analizar("LISPER[\"*\""));}
    @Test void rechazaComaFinal(){assertThrows(OrdenInvalidaException.class,()->parser.analizar("LISPER[\"*\",") );}
    @Test void limitaEntradasHostiles(){String enorme="A".repeat(1200);assertThrows(OrdenInvalidaException.class,()->parser.analizar(enorme));}
    @Test void limitaParametrosHostiles(){String enorme="A".repeat(501);assertThrows(OrdenInvalidaException.class,()->parser.analizar("X[\""+enorme+"\"]"));}
    @Test void laOrdenNoPuedeMutarDespuesDeValidarse(){var mutable=new java.util.ArrayList<>(java.util.List.of("dato"));Orden o=new Orden("X",mutable);mutable.set(0,"alterado");assertEquals("dato",o.parametros().get(0));assertThrows(UnsupportedOperationException.class,()->o.parametros().add("otro"));}
}
