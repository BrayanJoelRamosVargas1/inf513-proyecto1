package bo.edu.uagrm.inf513.grupo09sa.persistencia;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.regex.Pattern;
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
    String asunto(String comando,List<String> parametros) throws Exception {
        // Serialización independiente del analizador probado: conserva comillas y barras.
        return comando+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parametros);
    }
    @Test void cargasSqlAtravesDelAsuntoCompletoSeConservanComoDatos() throws Exception {
        var parser=new AnalizadorOrden();
        for(int i=0;i<CARGAS.size();i++) {
            String ci=Integer.toString(700000+i), carga=CARGAS.get(i);
            var orden=parser.analizar(asunto("INSPER",List.of(ci,carga,"Ficticio","Prueba","123","456","asunto"+i+"@example.com")));
            assertTrue(procesador.procesar("asunto-"+i,"qa@example.com",orden).exito());
            try(var c=ds.getConnection();var s=c.prepareStatement("select nombres from persona where ci=?")) {
                s.setString(1,ci);try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(carga,r.getString(1));}
            }
        }
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select (select count(*) from persona),(select count(*) from p1_usuario)")) {
            assertTrue(r.next());assertEquals(CARGAS.size(),r.getInt(1));assertEquals(0,r.getInt(2));
        }
    }
    @Test void inyeccionesEnCadaPosicionNumericaSeRechazanAntesDeCrearConfirmaciones() throws Exception {
        record Campo(String comando,List<String> parametros,int posicion){}
        var casos=List.of(
            new Campo("ASIOFE",List.of("1","1","1"),0),new Campo("ASIOFE",List.of("1","1","1"),1),new Campo("ASIOFE",List.of("1","1","1"),2),
            new Campo("CFGARE",List.of("1","1","teoria","50"),0),new Campo("CFGARE",List.of("1","1","teoria","50"),1),new Campo("CFGARE",List.of("1","1","teoria","50"),3),
            new Campo("INSPOST",List.of("1","1","referencia.pdf"),0),new Campo("INSPOST",List.of("1","1","referencia.pdf"),1),
            new Campo("REGMER",List.of("1","experiencia","50","Ensayo"),0),new Campo("REGMER",List.of("1","experiencia","50","Ensayo"),2),
            new Campo("REGEXA",List.of("1","50","Ensayo"),1),new Campo("REGAREA",List.of("1","teoria","50","Ensayo"),2),
            new Campo("GENRAN",List.of("1"),0),new Campo("REPEST",List.of("1"),0),new Campo("LISCFG",List.of("1"),0),
            new Campo("AUDCAL",List.of("p1_postulacion","1"),1),new Campo("ASIPLA",List.of("1","true"),0),
            new Campo("DESEMP",List.of("1","1","1,2","Resolución ficticia"),2));
        var parser=new AnalizadorOrden();int intentos=0;
        for(var caso:casos)for(String carga:CARGAS) {
            var parametros=new ArrayList<>(caso.parametros());parametros.set(caso.posicion(),carga);
            var r=procesador.procesar("numero-matriz-"+(intentos++),"admin@example.com",parser.analizar(asunto(caso.comando(),parametros)));
            assertFalse(r.exito(),caso.comando()+": "+carga);
            assertFalse(r.asunto().startsWith("CONFIRMACIÓN REQUERIDA"));
        }
        assertEquals(180,intentos);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select (select count(*) from p1_correo_control where token_hash is not null),(select count(*) from p1_auditoria),(select count(*) from p1_postulacion)")) {
            assertTrue(r.next());for(int i=1;i<=3;i++)assertEquals(0,r.getInt(i));
        }
    }
    @Test void sqlPersistidoYConsultadoDeNuevoNoSeEjecutaDesdeCatalogoNiAuditoria() throws Exception {
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("insert into p1_usuario(registro,nombres,apellidos,rol,correo) values('1','Admin','Ficticio','DECANO','admin@example.com')");
        }
        String carga="x'); DROP TABLE p1_usuario; --";
        var parser=new AnalizadorOrden();
        var desafio=procesador.procesar("cargo-sql","admin@example.com",parser.analizar(asunto("INSCAR",List.of(carga,carga,"true"))));
        var token=Pattern.compile("CONFIRMAR\\[\"([A-Za-z0-9_-]{43})\"\\]").matcher(desafio.cuerpo());assertTrue(token.find());
        assertTrue(procesador.procesar("cargo-confirmado","admin@example.com",parser.analizar(asunto("CONFIRMAR",List.of(token.group(1))))).exito());
        var listado=procesador.procesar("catalogo-sql","qa@example.com",parser.analizar(asunto("LISCAR",List.of(carga))));
        assertTrue(listado.exito());assertTrue(listado.cuerpo().contains(carga));
        var orden=parser.analizar(asunto("AUDCAL",List.of(carga,"1")));
        var pendiente=procesador.procesar("auditoria-sql","admin@example.com",orden);
        var segundo=Pattern.compile("CONFIRMAR\\[\"([A-Za-z0-9_-]{43})\"\\]").matcher(pendiente.cuerpo());assertTrue(segundo.find());
        var resultado=procesador.procesar("auditoria-confirmada","admin@example.com",new Orden("CONFIRMAR",List.of(segundo.group(1))));
        assertTrue(resultado.exito());assertEquals("Sin resultados.",resultado.cuerpo());
        assertTrue(repo.autorizado("admin@example.com","DECANO"));
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select nombre,unidad from p1_cargo")) {
            assertTrue(r.next());assertEquals(carga,r.getString(1));assertEquals(carga,r.getString(2));assertFalse(r.next());
        }
    }
    @Test void asuntosMalformadosYControlesNoPermitenApilarUnaSegundaOrden() throws Exception {
        String valido=asunto("INSPER",List.of("123","Ficticio","Apellido","Prueba","123","456","qa@example.com"));
        var parser=new AnalizadorOrden();
        for(String ataque:List.of(valido+"; LISUSU[\"*\"]",valido+" INSPER[]",valido.substring(0,valido.length()-1)+",]","AYUDA["+"[".repeat(1000),"LISPER[\"sin cierre]"))
            assertThrows(OrdenInvalidaException.class,()->parser.analizar(ataque));
        // Los controles se introducen en el asunto, sin escapes JSON, como en un correo hostil.
        for(String control:List.of("\r\nBcc: tercero@example.com","\u0000","\t")) {
            String crudo=valido.replace("Ficticio","Ficticio"+control);
            var r=procesador.procesar("control-"+UUID.randomUUID(),"qa@example.com",parser.analizar(crudo));
            assertFalse(r.exito());
        }
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select count(*) from persona")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
        persona("999","Operación posterior válida","posterior@example.com");
    }
}
