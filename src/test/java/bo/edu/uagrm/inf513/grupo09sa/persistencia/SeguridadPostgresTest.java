package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class SeguridadPostgresTest {
    PGSimpleDataSource ds; RepositorioPostgres repo; ProcesadorOrden procesador; String schema;
    @BeforeEach void preparar()throws Exception{
        ds=new PGSimpleDataSource();ds.setURL(System.getenv("TEST_DB_URL"));ds.setUser(System.getenv().getOrDefault("TEST_DB_USER","audit"));ds.setPassword(System.getenv().getOrDefault("TEST_DB_PASSWORD",""));
        schema="audit_"+UUID.randomUUID().toString().replace("-","");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("create schema "+schema);}
        ds.setCurrentSchema(schema);
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute(Files.readString(Path.of("database/schema.sql")));}
        repo=new RepositorioPostgres(ds);procesador=new ProcesadorOrden(new CatalogoComandos(),repo,"admin@example.com");
    }
    @AfterEach void limpiar()throws Exception{
        if(schema!=null)try(var c=ds.getConnection();var s=c.createStatement()){s.execute("drop schema "+schema+" cascade");}
    }
    Orden alta(){return new Orden("INSUSU",List.of("1","Ana","Perez","DECANO","70000000","admin@example.com","true"));}
    int secuencia;
    Resultado ordenar(String correo,String comando,String... argumentos){
        String id="flujo-"+(++secuencia);Resultado r=procesador.procesar(id,correo,new Orden(comando,List.of(argumentos)));
        return r.asunto().startsWith("CONFIRMACIÓN REQUERIDA")?confirmar(id+"-confirmado",correo,codigo(r)):r;
    }
    void iniciarFlujo()throws Exception{
        assertTrue(confirmar("inicio", "admin@example.com",codigo(procesador.procesar("alta-inicio","admin@example.com",alta()))).exito());
        assertTrue(ordenar("admin@example.com","INSUSU","2","Eva","Lopez","EVALUADOR","70000001","eva@example.com","true").exito());
        assertTrue(ordenar("admin@example.com","INSUSU","3","Luis","Diaz","POSTULANTE","70000002","luis@example.com","true").exito());
        var hoy=java.time.LocalDate.now();
        assertTrue(ordenar("admin@example.com","INSCON","AUX","Auxiliaturas",hoy.minusDays(1).toString(),hoy.plusDays(1).toString(),"ABIERTA","1").exito());
        assertTrue(ordenar("admin@example.com","INSCAR","Auxiliar","Facultad","true").exito());
        assertTrue(ordenar("luis@example.com","INSPOST","1","1","https://example.com/expediente.pdf").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","1","2","MERITOS_Y_EXAMEN").exito());
    }
    @Test void recorridoDeLosOchoCasosDeUsoConConfirmacionesYBaseReal()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","LISUSU","*").cuerpo().contains("luis@example.com"));
        assertTrue(ordenar("admin@example.com","LISUSU","*").cuerpo().startsWith("id |"));
        assertTrue(ordenar("luis@example.com","LISCON","*").cuerpo().startsWith("id |"));
        assertTrue(ordenar("luis@example.com","LISCAR","*").exito());
        assertTrue(ordenar("luis@example.com","LISPOST","*").cuerpo().contains("REGISTRADA"));
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple requisitos").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","85","Revisado").exito());
        assertEquals(83,numero("select puntaje_final::int from p1_postulacion where id=1"));
        assertTrue(ordenar("admin@example.com","GENRAN","1").cuerpo().contains("Luis"));
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("admin@example.com","AUDCAL","ASIPLA","1").cuerpo().contains("admin@example.com"));
        assertTrue(ordenar("admin@example.com","REPEST","1").cuerpo().contains("GANADOR"));
        assertTrue(ordenar("atacante@example.com","AYUDA").exito());
        assertEquals(1,numero("select count(*) from p1_expediente"));
    }
    @Test void examenExigeHabilitacionYRetirarPlazaNoHabilitaAlRechazado()throws Exception{
        iniciarFlujo();
        assertFalse(ordenar("eva@example.com","REGEXA","1","85","Revisado").exito());
        assertEquals(0,numero("select count(*) from p1_examen"));
        assertTrue(ordenar("admin@example.com","HABPOS","1","INHABILITADO","No cumple").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","1","false").exito());
        assertEquals(1,numero("select count(*) from p1_postulacion where estado='INHABILITADO' and not plaza_asignada"));
    }
    String codigo(Resultado r){var m=Pattern.compile("CONFIRMAR\\[\"([A-Za-z0-9_-]{43})\"\\]").matcher(r.cuerpo());assertTrue(m.find(),r.cuerpo());return m.group(1);}
    Resultado confirmar(String id,String correo,String token){return procesador.procesar(id,correo,new Orden("CONFIRMAR",List.of(token)));}
    int usuarios()throws Exception{return numero("select count(*) from p1_usuario");}
    int numero(String sql)throws Exception{try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    void ejecutarSql(String sql)throws Exception{try(var c=ds.getConnection();var s=c.createStatement()){s.execute(sql);}}
    @Test void fromFalsificadoNoCreaDecanoHastaDemostrarAccesoAlCorreo()throws Exception{
        Resultado desafio=procesador.procesar("alta-1","admin@example.com",alta());
        assertFalse(desafio.exito());assertEquals(0,usuarios());
        String token=codigo(desafio);
        assertFalse(confirmar("conf-ajena","atacante@example.com",token).exito());assertEquals(0,usuarios());
        assertFalse(confirmar("conf-falsa","admin@example.com","X".repeat(43)).exito());
        assertTrue(confirmar("conf-1","admin@example.com",token).exito());assertEquals(1,usuarios());
        assertTrue(confirmar("conf-1","admin@example.com",token).exito());assertEquals(1,usuarios());
        assertFalse(confirmar("conf-2","admin@example.com",token).exito());
    }
    @Test void tokenSoloEjecutaLaOrdenGuardadaYRevalidaRol()throws Exception{
        assertTrue(confirmar("arranque","admin@example.com",codigo(procesador.procesar("alta","admin@example.com",alta()))).exito());
        Orden cargo=new Orden("INSCAR",List.of("Ayudante","Facultad","true"));
        String token=codigo(procesador.procesar("cargo","admin@example.com",cargo));
        assertFalse(procesador.procesar("mutacion","admin@example.com",new Orden("CONFIRMAR",List.of(token,"LISUSU[\"*\"]"))).exito());
        ejecutarSql("update p1_usuario set activo=false");
        assertFalse(confirmar("revocado","admin@example.com",token).exito());assertEquals(0,numero("select count(*) from p1_cargo"));
    }
    @Test void tokenVencidoNoEjecuta()throws Exception{
        String token=codigo(procesador.procesar("caduca","admin@example.com",alta()));
        ejecutarSql("update p1_confirmacion set vence_en=now()-interval '1 minute'");
        assertFalse(confirmar("c","admin@example.com",token).exito());assertEquals(0,usuarios());
    }
    @Test void falloDeBaseRevierteNegocioYConsumoDelCodigo()throws Exception{
        String token=codigo(procesador.procesar("rollback","admin@example.com",alta()));
        assertThrows(SQLException.class,()->repo.confirmarOrden("c","admin@example.com",token,(id,o)->{
            repo.ejecutarIdempotente(id,"admin@example.com",o);throw new SQLException("Corte simulado","08006");
        }));
        assertEquals(0,usuarios());assertEquals(0,numero("select count(*) from p1_confirmacion where usado"));
        assertTrue(confirmar("c","admin@example.com",token).exito());assertEquals(1,usuarios());
    }
    @Test void errorFuncionalNoDejaTransaccionAbortadaNiCodigoReutilizable()throws Exception{
        assertTrue(confirmar("a","admin@example.com",codigo(procesador.procesar("u","admin@example.com",alta()))).exito());
        String token=codigo(procesador.procesar("duplicado","admin@example.com",alta()));
        Resultado error=confirmar("dup","admin@example.com",token);assertFalse(error.exito());assertTrue(error.cuerpo().contains("ya existe"));
        assertFalse(confirmar("otro","admin@example.com",token).exito());assertEquals(1,usuarios());
    }
    @Test void saturacionConservaOrdenYLaPromueveCuandoSeLiberaLaVentana()throws Exception{
        ejecutarSql("insert into p1_correo_control(mensaje_id,remitente,asunto,estado,admitido_en) select 'old-'||n,'persona-'||n||'@example.com','AYUDA[]','RESPONDIDO',now() from generate_series(1,1000) n");
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("legitimo","admin@example.com","AYUDA[]"));
        assertEquals(0,repo.promoverDiferidos(25));assertFalse(repo.correoProcesado("legitimo"));
        ejecutarSql("update p1_correo_control set admitido_en=now()-interval '2 hours' where estado='RESPONDIDO'");
        assertEquals(1,repo.promoverDiferidos(25));assertEquals("legitimo",repo.correosPendientes(25).get(0).id());
        assertEquals(RepositorioPostgres.Admision.DUPLICADO,repo.registrarEntrada("legitimo","admin@example.com","AYUDA[]"));
    }
    @Test void unRemitenteSaturadoNoBloqueaOtrosEnLaCola()throws Exception{
        ejecutarSql("insert into p1_correo_control(mensaje_id,remitente,estado,admitido_en) select 'spam-'||n,'spam@example.com','RESPONDIDO',now() from generate_series(1,60) n");
        repo.registrarEntrada("spam-diferido","spam@example.com","AYUDA[]");repo.registrarEntrada("bueno","admin@example.com","AYUDA[]");
        assertEquals(1,repo.promoverDiferidos(25));assertEquals("bueno",repo.correosPendientes(25).get(0).id());
    }
    @Test void limiteDeColaMantieneElExcedenteEnLaBandejaYPublicosNoPidenCodigo()throws Exception{
        ejecutarSql("insert into p1_correo_control(mensaje_id,remitente,estado) select 'pending-'||n,'spam@example.com','DIFERIDO' from generate_series(1,10000) n");
        assertEquals(RepositorioPostgres.Admision.SATURADO,repo.registrarEntrada("excedente","a@example.com","AYUDA[]"));
        assertFalse(repo.correosConocidos(List.of("excedente")).contains("excedente"));
        assertTrue(procesador.procesar("publico","atacante@example.com",new Orden("AYUDA",List.of())).exito());
    }
    @Test void confirmacionRecibidaATiempoNoVenceMientrasEstaDiferida()throws Exception{
        String token=codigo(procesador.procesar("original-cola","admin@example.com",alta()));
        ejecutarSql("insert into p1_correo_control(mensaje_id,remitente,estado,admitido_en) select 'limite-'||n,'admin@example.com','RESPONDIDO',now() from generate_series(1,60) n");
        repo.registrarEntrada("confirmacion-cola","admin@example.com","confirmar [ \""+token+"\" ]");
        assertEquals(0,repo.promoverDiferidos(25));
        ejecutarSql("update p1_correo_control set creado_en=now()-interval '2 hours' where mensaje_id='confirmacion-cola'");
        ejecutarSql("update p1_confirmacion set vence_en=now()-interval '1 hour'");
        assertTrue(confirmar("confirmacion-cola","admin@example.com",token).exito());assertEquals(1,usuarios());
    }
    @Test void migracionRepetidaConservaLasOrdenesDiferidas()throws Exception{
        repo.registrarEntrada("conservar","a@example.com","AYUDA[]");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute(Files.readString(Path.of("database/schema.sql")));}
        assertEquals(1,repo.promoverDiferidos(25));assertEquals("conservar",repo.correosPendientes(25).get(0).id());
    }
}
