package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.*;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class CicloCorreoPostgresTest {
    PGSimpleDataSource ds; String schema; RepositorioPostgres repo; ProcesadorOrden processor;
    AppConfig cfg=new AppConfig("imap","localhost",993,true,"localhost",465,true,true,"smtp","https://localhost/","audit","audit@example.com","dummy","jdbc:postgresql://localhost/audit","audit","dummy","admin@example.com",1,1,1,25,300,"INBOX");
    @BeforeEach void preparar()throws Exception{
        ds=new PGSimpleDataSource();ds.setURL(System.getenv("TEST_DB_URL"));ds.setUser(System.getenv().getOrDefault("TEST_DB_USER","audit"));ds.setPassword(System.getenv().getOrDefault("TEST_DB_PASSWORD",""));
        schema="audit_ciclo_"+UUID.randomUUID().toString().replace("-","");
        sql("create schema "+schema);ds.setCurrentSchema(schema);sql(Files.readString(Path.of("database/schema.sql")));
        repo=new RepositorioPostgres(ds);repo.marcarServicioInicializado("imap");processor=new ProcesadorOrden(new CatalogoComandos(),repo,"admin@example.com");
    }
    @AfterEach void limpiar()throws Exception{if(schema!=null)sql("drop schema "+schema+" cascade");}
    void sql(String sql)throws Exception{try(var c=ds.getConnection();var s=c.createStatement()){s.execute(sql);}}
    int cantidad(String tabla)throws Exception{try(var c=ds.getConnection();var s=c.createStatement();var rs=s.executeQuery("select count(*) from "+tabla)){rs.next();return rs.getInt(1);}}
    void ciclo(CorreoFalso mail)throws Exception{Main.ejecutarCiclo(cfg,mail,repo,processor,new AnalizadorOrden());}
    @Test void envioFallidoConservaRespuestaYNoDuplicaElResultado()throws Exception{
        var mail=new CorreoFalso();mail.entrada.add(new MensajeEntrada("uno","a@example.com","LISCON[\"*\"]"));
        ciclo(mail);assertEquals(1,cantidad("p1_resultado_orden"));assertTrue(mail.marcados.contains("uno"));
        mail.fallaEnvio=true;ciclo(mail);assertFalse(repo.correoProcesado("uno"));assertEquals(1,cantidad("p1_correo_control"));
        sql("update p1_correo_control set siguiente_intento=now()");mail.fallaEnvio=false;ciclo(mail);
        assertTrue(repo.correoProcesado("uno"));assertEquals(1,mail.respuestas.size());assertEquals(1,cantidad("p1_resultado_orden"));assertEquals(3,mail.liberaciones);
    }
    @Test void errorEnUnMensajeNoBloqueaElSiguiente()throws Exception{
        var mail=new CorreoFalso();mail.entrada.add(new MensajeEntrada("malo","a@example.com","AYUDA["));mail.entrada.add(new MensajeEntrada("bueno","a@example.com","AYUDA[]"));
        ciclo(mail);ciclo(mail);assertEquals(2,mail.respuestas.size());assertTrue(repo.correoProcesado("malo"));assertTrue(repo.correoProcesado("bueno"));
    }
    @Test void liberaReferenciasInclusoSiFallaLaLectura()throws Exception{
        var mail=new CorreoFalso();mail.fallaLectura=true;assertThrows(java.io.IOException.class,()->ciclo(mail));assertEquals(1,mail.liberaciones);
    }
    @Test void errorAlMarcarLeidoNoPierdeLaOrdenGuardada()throws Exception{
        var mail=new CorreoFalso();mail.fallaMarca=true;mail.entrada.add(new MensajeEntrada("persistido","a@example.com","AYUDA[]"));
        ciclo(mail);assertEquals(1,cantidad("p1_correo_control"));mail.fallaMarca=false;ciclo(mail);assertTrue(repo.correoProcesado("persistido"));assertEquals(1,mail.respuestas.size());
    }
    static class CorreoFalso implements PasarelaCorreo {
        List<MensajeEntrada> entrada=new ArrayList<>();List<String> respuestas=new ArrayList<>();Set<String> marcados=new HashSet<>();int liberaciones;boolean fallaEnvio,fallaLectura,fallaMarca;
        public List<String> identificadores(){return entrada.stream().map(MensajeEntrada::id).toList();}
        public List<MensajeEntrada> noProcesados(ConsultaProcesados consulta)throws Exception{if(fallaLectura)throw new java.io.IOException("Lectura simulada fallida");var conocidos=consulta.conocidos(identificadores());return entrada.stream().filter(m->!conocidos.contains(m.id())).toList();}
        public void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception{if(fallaEnvio)throw new java.io.IOException("Envío simulado fallido");respuestas.add(original.id());}
        public void marcarProcesado(MensajeEntrada mensaje)throws Exception{if(fallaMarca)throw new java.io.IOException("Marcado simulado fallido");marcados.add(mensaje.id());}
        public void liberarReferencias(){liberaciones++;}
        public void close(){}
    }
}
