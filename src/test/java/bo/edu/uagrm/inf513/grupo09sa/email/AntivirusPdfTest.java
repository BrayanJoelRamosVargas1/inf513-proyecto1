package bo.edu.uagrm.inf513.grupo09sa.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AntivirusPdfTest {
    @TempDir Path temporal;
    AdjuntoPdf pdf(){return new AdjuntoPdf("nombre recibido (1).pdf","%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    AntivirusPdf scanner(String salida,int segundos){
        String exe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        return new AntivirusPdf(List.of(exe,"-cp",cp,Simulador.class.getName(),salida),segundos,temporal);
    }
    void sinTemporales()throws Exception{try(var archivos=Files.list(temporal)){assertEquals(0,archivos.count());}}
    @Test void analizaLosBytesConNombreGeneradoYBorraElTemporal()throws Exception{
        assertTrue(scanner("0",5).aprobado(pdf()));sinTemporales();
    }
    @Test void deteccionYErrorDelMotorNuncaSeDeclaranLimpios()throws Exception{
        assertFalse(scanner("1",5).aprobado(pdf()));assertFalse(scanner("2",5).aprobado(pdf()));sinTemporales();
    }
    @Test void ejecutableAusenteNoApruebaYNoDejaBytesEnDisco()throws Exception{
        var scanner=new AntivirusPdf(List.of(temporal.resolve("no-existe").toString()),1,temporal);
        assertThrows(java.io.IOException.class,()->scanner.aprobado(pdf()));sinTemporales();
    }
    @Test void analisisColgadoSeDetieneYBorraElTemporal()throws Exception{
        assertThrows(java.io.IOException.class,()->scanner("esperar",1).aprobado(pdf()));sinTemporales();
    }
    /** Process fixture only, not an antivirus result or a production scanner. */
    public static class Simulador {
        public static void main(String[] args)throws Exception{
            Path pdf=Path.of(args[1]);
            if(!pdf.getFileName().toString().equals("documento.pdf")||!new String(Files.readAllBytes(pdf),java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))System.exit(9);
            if(args[0].equals("esperar")){Thread.sleep(60_000);return;}
            System.exit(Integer.parseInt(args[0]));
        }
    }
}
