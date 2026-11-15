package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Solo PostgreSQL de ensayo: cada prueba crea y retira su propio esquema. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@Timeout(120)
class InyeccionSqlPostgresTest {
    PGSimpleDataSource ds;
    RepositorioPostgres repo;
    ProcesadorOrden procesador;
    String schema;
    static final List<String> CARGAS=List.of(
        "' OR '1'='1", "x'); DROP TABLE persona; --",
        "1; SELECT pg_sleep(2); --", "x' UNION SELECT current_user --",
        "100%_\\comilla'", "José'); SELECT 1; --", "'/**/OR/**/1=1--",
        "\"; DROP TABLE p1_usuario; --", "$$; DROP TABLE persona; --", "\\'; SELECT 1; --");

    @BeforeEach void preparar() throws Exception {
        ds=new PGSimpleDataSource(); ds.setURL(System.getenv("TEST_DB_URL"));
        ds.setUser(System.getenv().getOrDefault("TEST_DB_USER","audit"));
        ds.setPassword(System.getenv().getOrDefault("TEST_DB_PASSWORD",""));
        schema="sql_audit_"+UUID.randomUUID().toString().replace("-","");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("create schema "+schema);}
        ds.setCurrentSchema(schema);
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute(Files.readString(Path.of("database/schema.sql")));}
        repo=new RepositorioPostgres(ds);
        procesador=new ProcesadorOrden(new CatalogoComandos(),repo,"");
    }
    @AfterEach void limpiar() throws Exception {
        if(schema!=null)try(var c=ds.getConnection();var s=c.createStatement()){s.execute("drop schema "+schema+" cascade");}
    }
    void persona(String ci,String nombre,String email) {
        var r=procesador.procesar("alta-"+ci,"qa@example.com",new Orden("INSPER",List.of(ci,nombre,"Apellido","Prueba","123","456",email)));
        assertTrue(r.exito(),r.cuerpo());
    }
    @Test void instruccionesSqlEnTextoSeGuardanLiteralmenteSinEjecutarse() throws Exception {
        for(int i=0;i<CARGAS.size();i++) {
            String ci=Integer.toString(900000+i), carga=CARGAS.get(i);
            persona(ci,carga,"sql"+i+"@example.com");
            try(var c=ds.getConnection();var s=c.prepareStatement("select nombres from persona where ci=?")) {
                s.setString(1,ci);
                try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(carga,r.getString(1));assertFalse(r.next());}
            }
        }
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from persona")) {
            assertTrue(r.next());assertEquals(CARGAS.size(),r.getInt(1));
        }
        // Una nueva operación normal sigue funcionando tras todas las cargas.
        persona("999999","Legítima","legitima@example.com");
        assertFalse(repo.hayUsuarios());
    }
    @Test void filtrosNoConviertenComodinesNiSqlEnListadoDeOtrosRegistros() {
        persona("111","Porcentaje%","porcentaje@example.com");
        persona("222","Sub_raya","subraya@example.com");
        persona("333","Barra\\dato","barra@example.com");
        for(var caso:List.of(List.of("%","111"),List.of("_","222"),List.of("\\","333"))) {
            var r=procesador.procesar("filtro-"+caso.get(1),"qa@example.com",new Orden("LISPER",List.of(caso.get(0))));
            assertTrue(r.exito());assertTrue(r.cuerpo().contains(caso.get(1)+" |"));
            for(String otro:List.of("111","222","333"))if(!otro.equals(caso.get(1)))assertFalse(r.cuerpo().contains(otro+" |"));
        }
        for(int i=0;i<CARGAS.size();i++) {
            var r=procesador.procesar("filtro-sql-"+i,"qa@example.com",new Orden("LISPER",List.of(CARGAS.get(i))));
            assertTrue(r.exito());assertEquals("Sin resultados.",r.cuerpo());
        }
    }
    @Test void identificadoresRemitentesYControlesDeAccesoNoInterpretanSql() throws Exception {
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("insert into p1_usuario(registro,nombres,apellidos,rol,telefono,correo,activo) values('1','Admin','Ficticio','DECANO','0','admin@example.com',true)");
        }
        assertTrue(repo.autorizado("admin@example.com","DECANO"));
        for(int i=0;i<CARGAS.size();i++) {
            String carga=CARGAS.get(i), id="sql-id-"+i+"-"+carga;
            assertFalse(repo.autorizado(carga,"DECANO"));
            assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada(id,"qa@example.com",carga));
            assertEquals(java.util.Set.of(id),repo.correosConocidos(List.of(id,"no-existe")));
            for(String comando:List.of("GENRAN","REPEST")) {
                var r=procesador.procesar("numero-"+comando+i,"admin@example.com",new Orden(comando,List.of(carga)));
                assertFalse(r.exito());assertFalse(r.asunto().startsWith("CONFIRMACIÓN REQUERIDA"));
            }
        }
        assertTrue(repo.autorizado("admin@example.com","DECANO"));
        persona("888888","Después de la prueba","despues@example.com");
    }
}
