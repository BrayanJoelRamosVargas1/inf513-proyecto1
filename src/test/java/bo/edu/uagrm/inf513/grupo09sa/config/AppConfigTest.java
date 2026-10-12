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
    private static AppConfig config(int poll,int max,String url){return new AppConfig("roundcube","correo",110,false,"correo",25,false,false,"roundcube",url,"grupo","grupo@correo","secreto","jdbc:postgresql://db/base","grupo","secreto",poll,10,30,max,300,"INBOX");}
}
