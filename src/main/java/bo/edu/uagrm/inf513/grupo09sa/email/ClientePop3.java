package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cliente POP3 de comandos por lote. UIDL evita descargar nuevamente 146 mensajes históricos. */
final class ClientePop3 {
    private final AppConfig cfg;
    ClientePop3(AppConfig cfg){this.cfg=cfg;}

    List<String> identificadores() throws Exception {
        try(Conexion c=conectar()){return new ArrayList<>(c.uidl().values());}
    }

    List<MensajeEntrada> nuevos(PasarelaCorreo.ConsultaProcesados consulta) throws Exception {
        try(Conexion c=conectar()){
            Map<Integer,String> ids=c.uidl();List<MensajeEntrada> salida=new ArrayList<>();
            Set<String> procesados=new HashSet<>();List<String> lote=new ArrayList<>(500);
            for(String id:ids.values()){lote.add(id);if(lote.size()==500){procesados.addAll(consulta.conocidos(lote));lote.clear();}}
            if(!lote.isEmpty())procesados.addAll(consulta.conocidos(lote));
            for(var e:ids.entrySet()){
                if(procesados.contains(e.getValue()))continue;
                byte[] raw=c.multilinea("TOP "+e.getKey()+" 0");
                MimeMessage m=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw));
                String remitente=((InternetAddress)m.getFrom()[0]).getAddress();
                salida.add(new MensajeEntrada(e.getValue(),remitente,m.getSubject()));
                if(salida.size()>=cfg.maxMessagesPerCycle())break;
            }
            return salida;
        }
    }

    private Conexion conectar() throws Exception {
        Socket s=new Socket();
        s.connect(new InetSocketAddress(cfg.imapHost(),cfg.imapPort()),cfg.connectSeconds()*1000);
        s.setSoTimeout(cfg.readSeconds()*1000);
        Conexion c=new Conexion(s);c.ok(c.linea());c.comando("USER "+cfg.mailUser());c.comando("PASS "+cfg.mailPassword());return c;
    }

    private static final class Conexion implements AutoCloseable {
        private static final int MAX_RESPUESTA=2*1024*1024;
        private final Socket socket;private final BufferedReader in;private final BufferedWriter out;
        Conexion(Socket socket)throws IOException{this.socket=socket;in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.ISO_8859_1));out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.ISO_8859_1));}
        String linea()throws IOException{String l=in.readLine();if(l==null)throw new EOFException("POP3 cerró la conexión");return l;}
        void ok(String l){if(!l.startsWith("+OK"))throw new IllegalStateException("POP3 rechazó la operación: "+l);}
        void comando(String cmd)throws IOException{out.write(cmd+"\r\n");out.flush();ok(linea());}
        byte[] multilinea(String cmd)throws IOException{out.write(cmd+"\r\n");out.flush();ok(linea());ByteArrayOutputStream b=new ByteArrayOutputStream();for(;;){String l=linea();if(l.equals("."))break;if(l.length()>16_384||b.size()+l.length()>MAX_RESPUESTA)throw new IOException("Respuesta POP3 excede el límite seguro");if(l.startsWith(".."))l=l.substring(1);b.write(l.getBytes(StandardCharsets.ISO_8859_1));b.write('\r');b.write('\n');}return b.toByteArray();}
        Map<Integer,String> uidl()throws IOException{String text=new String(multilinea("UIDL"),StandardCharsets.ISO_8859_1);Map<Integer,String> r=new LinkedHashMap<>();for(String l:text.split("\\r?\\n")){String[] p=l.trim().split("\\s+",2);if(p.length==2)r.put(Integer.parseInt(p[0]),p[1]);}return r;}
        public void close()throws IOException{IOException fallo=null;try{out.write("QUIT\r\n");out.flush();}catch(IOException ex){fallo=ex;}try{socket.close();}catch(IOException ex){if(fallo==null)fallo=ex;else fallo.addSuppressed(ex);}if(fallo!=null)throw fallo;}
    }
}
