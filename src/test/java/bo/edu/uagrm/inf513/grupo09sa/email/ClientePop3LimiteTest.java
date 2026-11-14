package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientePop3LimiteTest {
    @Test void rechazaMensajeGrandeSinDescargarloNiBloquearLaBandeja()throws Exception {
        try(var servidor=new ServerSocket(0)){
            servidor.setSoTimeout(5000);
            var executor=Executors.newSingleThreadExecutor();
            try{
                var tarea=executor.submit(()->{
                    try(var socket=servidor.accept();var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));var out=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.US_ASCII)){
                        out.print("+OK prueba\r\n");out.flush();String linea;
                        while((linea=in.readLine())!=null){
                            if(linea.startsWith("USER ")||linea.startsWith("PASS "))out.print("+OK\r\n");
                            else if(linea.equals("UIDL"))out.print("+OK\r\n1 uid-grande\r\n.\r\n");
                            else if(linea.equals("TOP 1 0"))out.print("+OK\r\nFrom: demo@example.com\r\nSubject: INSPOST[\"1\",\"1\",\"ADJUNTO\"]\r\n\r\n.\r\n");
                            else if(linea.equals("LIST 1"))out.print("+OK 1 5000000\r\n");
                            else if(linea.equals("QUIT")){out.print("+OK\r\n");out.flush();return true;}
                            else throw new AssertionError("No debe descargar el mensaje: "+linea);
                            out.flush();
                        }
                        return false;
                    }catch(IOException ex){throw new UncheckedIOException(ex);}
                });
                var cfg=new AppConfig("pop3","127.0.0.1",servidor.getLocalPort(),false,"127.0.0.1",25,false,false,"smtp","https://localhost/","demo","demo@example.com","dummy","jdbc:postgresql://localhost/test","demo","dummy","",1,2,2,25,300,"INBOX");
                var recibidos=new ClientePop3(cfg).nuevos(ids->Set.of());
                assertEquals(1,recibidos.size());assertTrue(recibidos.get(0).asunto().startsWith("ADJUNTO_RECHAZADO"));
                assertTrue(tarea.get(5,TimeUnit.SECONDS));
            }finally{executor.shutdownNow();}
        }
    }
}
