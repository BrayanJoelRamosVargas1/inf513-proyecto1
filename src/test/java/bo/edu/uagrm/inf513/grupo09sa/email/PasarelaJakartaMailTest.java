package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PasarelaJakartaMailTest {
    static AppConfig config(int port){return new AppConfig("imap","127.0.0.1",port,false,"127.0.0.1",port,false,false,"smtp","https://correo/","grupo","grupo@correo","dummy","jdbc:postgresql://db/base","grupo","dummy","admin@correo",1,1,1,25,300,"INBOX");}
    @Test void smtpEImapTienenLimitesDeLecturaEscrituraYConexion(){
        var p=PasarelaJakartaMail.propiedades(config(25));
        for(String protocolo:new String[]{"smtp","imap","imaps"})for(String propiedad:new String[]{"connectiontimeout","timeout","writetimeout"})assertEquals("1000",p.getProperty("mail."+protocolo+"."+propiedad));
    }
    @Test void smtpSilenciosoNoCongelaElServicio()throws Exception{
        try(ServerSocket servidor=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            var pool=Executors.newSingleThreadExecutor();
            var remoto=pool.submit(()->{try(var socket=servidor.accept()){Thread.sleep(2500);}catch(Exception ignored){}});
            long inicio=System.nanoTime();
            try(Transport smtp=Session.getInstance(PasarelaJakartaMail.propiedades(config(servidor.getLocalPort()))).getTransport("smtp")){
                assertThrows(MessagingException.class,()->smtp.connect("127.0.0.1",servidor.getLocalPort(),null,null));
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio)<2200,"La conexión no respetó el límite de lectura");
            }finally{remoto.cancel(true);pool.shutdownNow();}
        }
    }
    @Test void correoPersistidoSeMarcaAntesDeLiberarReferencias()throws Exception{
        try(var pasarela=new PasarelaJakartaMail(config(25))){
            var campo=PasarelaJakartaMail.class.getDeclaredField("mensajes");campo.setAccessible(true);
            @SuppressWarnings("unchecked") var mapa=(java.util.Map<String,jakarta.mail.Message>)campo.get(pasarela);
            var mensaje=new jakarta.mail.internet.MimeMessage(Session.getInstance(new java.util.Properties()));
            mapa.put("persistido",mensaje);
            pasarela.marcarProcesado(new MensajeEntrada("persistido","a@example.com","AYUDA[]"));
            pasarela.liberarReferencias();
            assertTrue(mensaje.isSet(jakarta.mail.Flags.Flag.SEEN));assertEquals(0,pasarela.referenciasRetenidas());
        }
    }
    @Test void referenciasDelLoteSeLiberan()throws Exception{
        try(var pasarela=new PasarelaJakartaMail(config(25))){
            var campo=PasarelaJakartaMail.class.getDeclaredField("mensajes");campo.setAccessible(true);
            @SuppressWarnings("unchecked") var mapa=(java.util.Map<String,jakarta.mail.Message>)campo.get(pasarela);
            for(int ciclo=0;ciclo<100;ciclo++){
                for(int i=0;i<25;i++)mapa.put(ciclo+":"+i,new jakarta.mail.internet.MimeMessage(Session.getInstance(new java.util.Properties())));
                pasarela.liberarReferencias();assertEquals(0,pasarela.referenciasRetenidas());
            }
        }
    }
}
