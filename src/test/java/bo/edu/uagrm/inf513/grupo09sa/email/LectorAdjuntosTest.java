package bo.edu.uagrm.inf513.grupo09sa.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class LectorAdjuntosTest {
    private static final byte[] PDF="%PDF-1.4\narchivo de prueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    MimeMessage mensaje(String nombre,String texto)throws Exception {
        var mensaje=new MimeMessage(Session.getInstance(new Properties()));
        var multipart=new MimeMultipart();
        var cuerpo=new MimeBodyPart();cuerpo.setText("Expediente adjunto");multipart.addBodyPart(cuerpo);
        var adjunto=new MimeBodyPart();adjunto.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(texto.getBytes(StandardCharsets.US_ASCII),"application/pdf")));adjunto.setFileName(nombre);multipart.addBodyPart(adjunto);
        mensaje.setContent(multipart);mensaje.saveChanges();
        var raw=new java.io.ByteArrayOutputStream();mensaje.writeTo(raw);
        return new MimeMessage(Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(raw.toByteArray()));
    }
    @Test void extraePdfSinConfundirElTextoDelCorreo()throws Exception {
        var entrada=new MensajeEntrada("m1","demo@example.com","INSPOST[\"1\",\"2\",\"ADJUNTO\"]");
        var salida=LectorAdjuntos.completar(entrada,mensaje("expediente.pdf",new String(PDF,StandardCharsets.US_ASCII)));
        assertEquals(1,salida.adjuntos().size());assertArrayEquals(PDF,salida.adjuntos().get(0).contenido());
        assertEquals(64,salida.adjuntos().get(0).sha256().length());
    }
    @Test void rechazaExtensionInvalidaYFalsaCabecera()throws Exception {
        var entrada=new MensajeEntrada("m1","demo@example.com","INSPOST[]");
        assertTrue(LectorAdjuntos.completar(entrada,mensaje("archivo.exe",new String(PDF,StandardCharsets.US_ASCII))).asunto().startsWith("ADJUNTO_RECHAZADO"));
        assertTrue(LectorAdjuntos.completar(entrada,mensaje("archivo.pdf","no es PDF")).asunto().startsWith("ADJUNTO_RECHAZADO"));
    }
    @Test void impideRutasYArchivosExcesivos() {
        assertThrows(IllegalArgumentException.class,()->new AdjuntoPdf("../archivo.pdf",PDF));
        byte[] grande=new byte[AdjuntoPdf.MAX_BYTES+1];System.arraycopy(PDF,0,grande,0,5);
        assertThrows(IllegalArgumentException.class,()->new AdjuntoPdf("grande.pdf",grande));
    }
    @Test void contenidoEsInmutable() {
        byte[] original=PDF.clone();var adjunto=new AdjuntoPdf("uno.pdf",original);
        original[0]=0;byte[] copia=adjunto.contenido();copia[0]=0;assertEquals('%',adjunto.contenido()[0]);
    }
    @Test void ignoraAdjuntosDeComandosQueNoCarganExpediente()throws Exception {
        var entrada=new MensajeEntrada("m1","demo@example.com","AYUDA[]");
        assertEquals(entrada,LectorAdjuntos.completar(entrada,mensaje("archivo.exe","ejecutable")));
    }
}
