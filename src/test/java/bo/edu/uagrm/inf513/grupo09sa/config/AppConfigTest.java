package bo.edu.uagrm.inf513.grupo09sa.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @Test void rechazaIntervalosYLimitesPeligrosos(){
        assertThrows(IllegalArgumentException.class,()->config(0,25,"https://correo/"));
        assertThrows(IllegalArgumentException.class,()->config(10,0,"https://correo/"));
        assertThrows(IllegalArgumentException.class,()->config(10,501,"https://correo/"));
    }
    @Test void exigeHttpsParaRoundcube(){assertThrows(IllegalArgumentException.class,()->config(10,25,"http://correo/"));}
    @Test void aceptaConfiguracionAcotada(){assertDoesNotThrow(()->config(10,25,"https://correo/"));}
    @Test void normalizaProtocolosParaQueJakartaEncuentreElProveedor(){
        var cfg=new AppConfig("IMAPS","correo",993,true,"correo",465,true,true,"SMTP","https://correo/","grupo","grupo@correo","dummy","jdbc:postgresql://db/base","grupo","dummy","admin@correo",10,10,30,25,300,"INBOX");
        assertEquals("imaps",cfg.inboundProtocol());assertEquals("smtp",cfg.outboundMode());
    }
    private static AppConfig config(int poll,int max,String url){return new AppConfig("roundcube","correo",110,false,"correo",25,false,false,"roundcube",url,"grupo","grupo@correo","secreto","jdbc:postgresql://db/base","grupo","secreto","admin@correo",poll,10,30,max,300,"INBOX");}
}
