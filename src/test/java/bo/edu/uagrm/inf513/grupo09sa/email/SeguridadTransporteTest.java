package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.URI;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SeguridadTransporteTest {
    static AppConfig config(String protocolo,String host,int port,boolean tls,String clave){
        return new AppConfig(protocolo,host,port,tls,"localhost",587,false,true,"smtp","https://correo/","grupo","grupo@example.com",clave,"jdbc:postgresql://localhost/test","grupo","db-secreto","",1,2,2,25,300,"INBOX");
    }
    @Test void noEnviaCredencialesPop3EnClaroCuandoSePideTls()throws Exception{
        try(var servidor=new ServerSocket(0)){
            servidor.setSoTimeout(5000);
            var executor=Executors.newSingleThreadExecutor();
            try{
                var primero=executor.submit(()->{try(var socket=servidor.accept()){socket.setSoTimeout(3000);return socket.getInputStream().read();}});
                assertThrows(IOException.class,()->new ClientePop3(config("pop3","127.0.0.1",servidor.getLocalPort(),true,"dummy")).identificadores());
                assertEquals(22,primero.get(5,TimeUnit.SECONDS),"Debe iniciar un handshake TLS, nunca USER/PASS en claro");
            }finally{executor.shutdownNow();}
        }
    }
    @Test void rechazaLineaPop3ExcesivaAntesDeLeerlaCompleta()throws Exception{
        try(var servidor=new ServerSocket(0)){
            servidor.setSoTimeout(5000);
            var executor=Executors.newSingleThreadExecutor();
            try{
                var remoto=executor.submit(()->{try(var socket=servidor.accept()){socket.getOutputStream().write(("+OK "+"\r".repeat(17000)).getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();Thread.sleep(1000);return socket.getInputStream().read();}});
                IOException error=assertThrows(IOException.class,()->new ClientePop3(config("pop3","127.0.0.1",servidor.getLocalPort(),false,"dummy")).identificadores());
                assertTrue(error.getMessage().contains("límite"));
                assertEquals(-1,remoto.get(5,TimeUnit.SECONDS),"La conexión fallida debe cerrarse sin enviar claves");
            }finally{executor.shutdownNow();}
        }
    }
    @Test void exigeCifradoEnPop3RemotoYEvitaInyeccionDeComandos(){
        assertThrows(IllegalArgumentException.class,()->config("pop3","correo.example.com",110,false,"dummy"));
        assertThrows(IllegalArgumentException.class,()->config("pop3","127.0.0.1",110,false,"dummy\r\nDELE 1"));
    }
    @Test void smtpAutenticadoEImapExigenStartTls(){
        var p=PasarelaJakartaMail.propiedades(config("imap","localhost",143,false,"dummy"));
        assertEquals("true",p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true",p.getProperty("mail.imap.starttls.required"));
    }
    @Test void redireccionesNoPuedenCambiarOrigenNiBajarAHtpp(){
        URI base=URI.create("https://correo.example.com/roundcube/");
        assertDoesNotThrow(()->ClienteRoundcube.validarRedireccion(base,"?_task=mail"));
        assertDoesNotThrow(()->ClienteRoundcube.validarRedireccion(base,"https://correo.example.com:443/roundcube/"));
        for(String destino:new String[]{"http://correo.example.com/","https://atacante.example/","//atacante.example/","https://correo.example.com:444/","https://user@correo.example.com/"})
            assertThrows(org.apache.hc.core5.http.ProtocolException.class,()->ClienteRoundcube.validarRedireccion(base,destino));
    }
    @Test void imprimirConfiguracionNoExponeClaves(){
        String texto=config("pop3","localhost",995,true,"mail-secreto").toString();
        assertFalse(texto.contains("mail-secreto"));assertFalse(texto.contains("db-secreto"));
    }
}
