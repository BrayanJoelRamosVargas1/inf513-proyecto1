package bo.edu.uagrm.inf513.grupo09sa.email;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;
import java.util.ArrayList;
import java.util.List;

/** Extract only attachments belonging to INSPOST, without interpreting PDF contents. */
final class LectorAdjuntos {
    static boolean requiereFuente(String asunto) {
        return asunto != null && asunto.matches("(?is)^\\s*(?:INSPOST|DOCPOST)\\s*\\[.*");
    }
    static MensajeEntrada completar(MensajeEntrada entrada, Part mime) throws Exception {
        if (!requiereFuente(entrada.asunto())) return entrada;
        try {
            List<AdjuntoPdf> archivos = new ArrayList<>();
            recorrer(mime, archivos, 0, new int[]{0});
            return new MensajeEntrada(entrada.id(), entrada.remitente(), entrada.asunto(), archivos);
        } catch (IllegalArgumentException ex) {
            // Queue an ordinary syntax error for the sender rather than retrying poison mail forever.
            return new MensajeEntrada(entrada.id(), entrada.remitente(), "ADJUNTO_RECHAZADO: " + ex.getMessage());
        }
    }
    private static void recorrer(Part parte, List<AdjuntoPdf> salida, int nivel, int[] cantidad) throws Exception {
        if (nivel > 8 || ++cantidad[0] > 50) throw new IllegalArgumentException("Estructura MIME demasiado compleja");
        if (parte.isMimeType("multipart/*")) {
            Multipart contenido = (Multipart) parte.getContent();
            if (contenido.getCount() > 50) throw new IllegalArgumentException("Demasiadas partes MIME");
            for (int i=0;i<contenido.getCount();i++) recorrer(contenido.getBodyPart(i),salida,nivel+1,cantidad);
        } else if (parte.getFileName()!=null || Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
            if (!salida.isEmpty()) throw new IllegalArgumentException("Se admite un PDF por orden");
            String nombre = parte.getFileName()==null ? "expediente.pdf" : MimeUtility.decodeText(parte.getFileName());
            try (var in = parte.getInputStream()) {
                salida.add(new AdjuntoPdf(nombre, in.readNBytes(AdjuntoPdf.MAX_BYTES+1)));
            }
        }
    }
}
