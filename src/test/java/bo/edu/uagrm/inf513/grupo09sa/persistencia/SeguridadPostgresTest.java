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
    @Test void ejemplosLiteralesDeLaPlataformaInsertanYListanPersona()throws Exception {
        var parser=new AnalizadorOrden();
        Orden orden=parser.analizar("INSPER[\"4715292\",\"Juan Carlos\",\"Perez Seras\",\"Estudiante\",\"33554433\",\"71055123\",\"juanperez@uagrm.edu.bo\"]");
        Resultado insertado=procesador.procesar("persona-1","remitente@example.com",orden);
        assertTrue(insertado.exito());assertEquals("Éxito: Registro insertado correctamente",insertado.cuerpo());
        assertTrue(procesador.procesar("persona-1","remitente@example.com",orden).exito());
        assertEquals(1,numero("select count(*) from persona"));
        assertEquals(0,usuarios());
        Resultado listado=procesador.procesar("persona-lista","remitente@example.com",parser.analizar("LISPER[\"*\"]"));
        assertTrue(listado.exito());assertTrue(listado.cuerpo().contains("4715292 | Juan Carlos | Perez Seras"));
        Resultado duplicado=procesador.procesar("persona-2","remitente@example.com",orden);
        assertFalse(duplicado.exito());assertTrue(duplicado.cuerpo().startsWith("Error:"));
        assertEquals(1,numero("select count(*) from persona"));
    }
    @Test void filtroDePersonaEscapaComodinesEInyeccionSql()throws Exception {
        var parser=new AnalizadorOrden();
        Resultado incorrecto=procesador.procesar("persona-invalida","a@example.com",parser.analizar("INSPER[\"4715292\",\"Juan\"]"));
        assertFalse(incorrecto.exito());assertEquals(0,numero("select count(*) from persona"));
        assertTrue(procesador.procesar("persona-filtro","a@example.com",new Orden("LISPER",List.of("%\u0027 OR 1=1 --"))).exito());
        assertEquals(0,numero("select count(*) from persona"));
    }
    @Test void cargaPdfRealTrasConfirmarYNoLoDuplica()throws Exception {
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSCAR","Segundo cargo","Facultad","true").exito());
        byte[] bytes="%PDF-1.4\nexpediente de prueba\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var archivo=new bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf("expediente.pdf",bytes);
        Orden orden=new Orden("INSPOST",List.of("1","2","ADJUNTO"));
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("correo-pdf","luis@example.com","INSPOST[]",List.of(archivo)));
        assertEquals(RepositorioPostgres.Admision.DUPLICADO,repo.registrarEntrada("correo-pdf","luis@example.com","INSPOST[]",List.of(archivo)));
        Resultado desafio=procesador.procesar("correo-pdf","luis@example.com",orden);
        assertEquals(1,numero("select count(*) from p1_postulacion"));
        String token=codigo(desafio);
        assertTrue(confirmar("pdf-confirmado","luis@example.com",token).exito());
        assertTrue(confirmar("pdf-confirmado","luis@example.com",token).exito());
        assertEquals(2,numero("select count(*) from p1_postulacion"));
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select contenido,sha256,nombre_archivo from p1_expediente where contenido is not null")){
            assertTrue(r.next());assertArrayEquals(bytes,r.getBytes(1));assertEquals(archivo.sha256(),r.getString(2));assertEquals("expediente.pdf",r.getString(3));assertFalse(r.next());
        }
    }
    @Test void adjuntoAusenteNoCreaPostulacionParcial()throws Exception {
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSCAR","Segundo cargo","Facultad","true").exito());
        Resultado resultado=ordenar("luis@example.com","INSPOST","1","2","ADJUNTO");
        assertFalse(resultado.exito());assertTrue(resultado.cuerpo().contains("No se recibió el PDF"));
        assertEquals(1,numero("select count(*) from p1_postulacion"));
    }
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
    @Test void correccionDeRequisitoConservaHistorialYPermiteHabilitar()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","false","Falta documento").exito());
        assertFalse(ordenar("admin@example.com","HABPOS","1","HABILITADO","Revisar").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Documento subsanado").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Verificado").exito());
        assertEquals(2,numero("select count(*) from p1_evaluacion_requisito"));
    }
    @Test void inhabilitarGanadorLiberaPlazaSinRomperRestricciones()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","85","Revisado").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Revisado").exito());
        assertEquals(1,numero("select count(*) from p1_postulacion where estado='GANADOR' and plaza_asignada"));
        assertTrue(ordenar("admin@example.com","HABPOS","1","INHABILITADO","Requisito revocado").exito());
        assertEquals(1,numero("select count(*) from p1_postulacion where estado='INHABILITADO' and not plaza_asignada"));
    }
    void habilitarEnsayo()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
    }
    @Test void auditoriaNoPermiteInflarMeritosRepitiendoElMismoCriterio()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","30","Primero").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","EXPERIENCIA","30","Repetido").exito());
        assertEquals(30,numero("select puntaje_meritos::int from p1_postulacion where id=1"));
        assertEquals(2,numero("select count(*) from p1_merito"),"El historial se conserva");
    }
    @Test void auditoriaUnaCorreccionDeExamenNoDaDosVotosAlEvaluador()throws Exception{
        habilitarEnsayo();
        assertTrue(ordenar("eva@example.com","REGEXA","1","20","Original").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","80","Corregido").exito());
        assertEquals(80,numero("select puntaje_examen::int from p1_postulacion where id=1"));
        assertEquals(2,numero("select count(*) from p1_examen"));
    }
    @Test void auditoriaExamenNoAceptaRequisitoRevocadoDespuesDeHabilitar()throws Exception{
        habilitarEnsayo();
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","false","Documento revocado").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","1","100","Intenta saltar requisito").exito());
        assertEquals(0,numero("select count(*) from p1_examen"));
    }
    @Test void auditoriaNoAsignaPlazaSinMeritosYExamenRegistrados()throws Exception{
        habilitarEnsayo();
        assertFalse(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertEquals(0,numero("select count(*) from p1_postulacion where plaza_asignada"));
    }
    @Test void auditoriaNotasNoCambianDespuesDeAsignarUnaPlaza()throws Exception{
        habilitarEnsayo();
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","85","Revisado").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","1","0","Cambiar ganador").exito());
        assertFalse(ordenar("eva@example.com","EVAREQ","1","Identidad","false","Cambiar ganador").exito());
        assertEquals(83,numero("select puntaje_final::int from p1_postulacion where id=1"));
        assertTrue(ordenar("admin@example.com","ASIPLA","1","false").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","0","Correccion autorizada tras retirar plaza").exito());
    }
    @Test void auditoriaUnDesconocidoNoPuedeOcuparAlmacenamientoPdf()throws Exception{
        var a=new bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf("expediente.pdf","%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("pdf-sin-rol","desconocido@example.com","INSPOST[\"1\",\"1\",\"ADJUNTO\"]",List.of(a)));
        assertEquals(0,numero("select count(*) from p1_adjunto_correo"));
        assertEquals(1,numero("select count(*) from p1_correo_control"),"Se conserva la orden para responder el rechazo");
    }
    @Test void auditoriaCupoPdfSeRecuperaSinBorrarConfirmacionesPendientes()throws Exception{
        iniciarFlujo();
        byte[] contenido=new byte[2*1024*1024];
        System.arraycopy("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,contenido,0,8);
        var pdf=new bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf("expediente.pdf",contenido);
        for(int i=0;i<4;i++)assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("cupo-"+i,"luis@example.com","INSPOST[]",List.of(pdf)));
        assertEquals(RepositorioPostgres.Admision.SATURADO,repo.registrarEntrada("exceso","luis@example.com","INSPOST[]",List.of(pdf)));
        assertEquals(4,numero("select count(*) from p1_adjunto_correo"));
        assertEquals(0,numero("select count(*) from p1_correo_control where mensaje_id='exceso'"),"La orden permanece en el correo, sin pérdida por saturación");
        procesador.procesar("cupo-0","luis@example.com",new Orden("INSPOST",List.of("1","2","ADJUNTO")));
        repo.confirmarRespuesta("cupo-0");
        assertEquals(RepositorioPostgres.Admision.SATURADO,repo.registrarEntrada("exceso","luis@example.com","INSPOST[]",List.of(pdf)),"La confirmación pendiente conserva su PDF");
        repo.confirmarRespuesta("cupo-1");
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("exceso","luis@example.com","INSPOST[]",List.of(pdf)));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='cupo-0'"));
        assertEquals(4,numero("select count(*) from p1_adjunto_correo"));
    }
    @Test void auditoriaMeritosPromedianEvaluadoresSinDuplicarElCriterio()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSUSU","4","Eva Dos","Lopez","EVALUADOR","70000004","eva2@example.com","true").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","1","4","MERITOS_Y_EXAMEN").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","20","Inicial").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","experiencia","40","Correccion").exito());
        assertTrue(ordenar("eva2@example.com","REGMER","1","EXPERIENCIA","60","Segunda evaluadora").exito());
        assertEquals(50,numero("select puntaje_meritos::int from p1_postulacion where id=1"));
        assertEquals(3,numero("select count(*) from p1_merito"));
    }
    @Test void auditoriaNotaCeroRegistradaNoSeConfundeConEvaluacionAusente()throws Exception{
        habilitarEnsayo();
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","0","Cero real").exito());
        assertFalse(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","0","Cero real").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
    }
    @Test void auditoriaPlazaRespetaRankingYCongelaTodaLaConvocatoria()throws Exception{
        habilitarEnsayo();
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","85","Revisado").exito());
        assertTrue(ordenar("admin@example.com","INSUSU","4","Ana","Perez","POSTULANTE","70000004","ana@example.com","true").exito());
        assertTrue(ordenar("ana@example.com","INSPOST","1","1","https://example.com/ana.pdf").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","2","2","MERITOS_Y_EXAMEN").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","2","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","2","HABILITADO","Cumple").exito());
        assertTrue(ordenar("eva@example.com","REGMER","2","Experiencia","50","Revisado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","2","50","Revisado").exito());
        assertFalse(ordenar("admin@example.com","ASIPLA","2","true").exito(),"No puede saltarse al primer lugar");
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","2","100","Alterar ranking cerrado").exito());
        assertFalse(ordenar("eva@example.com","REGMER","2","Experiencia","100","Alterar ranking cerrado").exito());
        assertFalse(ordenar("admin@example.com","HABPOS","2","INHABILITADO","Cambiar elegibles").exito());
        assertTrue(ordenar("ana@example.com","INSPOST","1","999","https://example.com/extra.pdf").cuerpo().contains("Retire primero"));
        assertEquals(50,numero("select puntaje_final::int from p1_postulacion where id=2"));
        assertEquals(1,numero("select count(*) from p1_postulacion where plaza_asignada"));
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
    @Test void auditoriaReconstruyeLaCalificacionAnteriorYPosterior()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Documento revisado").exito());
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select actor,detalle from p1_auditoria where accion='REGMER'")){
            assertTrue(r.next());assertEquals("eva@example.com",r.getString(1));
            var d=new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getString(2));
            assertEquals("80",d.path("parametros").get(2).asText());
            assertEquals(0,d.path("anterior").path("puntaje_meritos").asInt());
            assertEquals(80,d.path("posterior").path("puntaje_meritos").asInt());
            assertEquals(32,d.path("posterior").path("puntaje_final").asInt());assertFalse(r.next());
        }
        String historial=ordenar("admin@example.com","AUDCAL","REGMER","1").cuerpo();
        assertTrue(historial.contains("Documento revisado"));assertTrue(historial.contains("posterior"));
    }
    @Test void falloDeAuditoriaRevierteTambienUnaLlamadaDirecta()throws Exception{
        iniciarFlujo();
        ejecutarSql("create function rechazar_auditoria() returns trigger language plpgsql as $$ begin raise exception 'Auditoria no disponible'; end $$; create trigger qa_auditoria before insert on p1_auditoria for each row execute function rechazar_auditoria()");
        assertThrows(SQLException.class,()->repo.ejecutar("eva@example.com",new Orden("REGMER",List.of("1","Experiencia","80","Revisado"))));
        assertEquals(0,numero("select count(*) from p1_merito"));
        assertEquals(0,numero("select puntaje_final::int from p1_postulacion where id=1"));
        ejecutarSql("drop trigger qa_auditoria on p1_auditoria");
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertEquals(1,numero("select count(*) from p1_merito"));
    }
    @Test void correosSimultaneosRecalculanLasNotasSinPerderAportes()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertTrue(ordenar("admin@example.com","INSUSU","4","Eva Dos","Lopez","EVALUADOR","70000004","eva2@example.com","true").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","1","4","MERITOS_Y_EXAMEN").exito());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var inicio=new java.util.concurrent.CountDownLatch(1);
        try{
            var a=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("nota-a","eva@example.com",new Orden("REGEXA",List.of("1","85","Primera")));});
            var b=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("nota-b","eva2@example.com",new Orden("REGEXA",List.of("1","95","Segunda")));});
            inicio.countDown();assertTrue(a.get(15,java.util.concurrent.TimeUnit.SECONDS).exito());assertTrue(b.get(15,java.util.concurrent.TimeUnit.SECONDS).exito());
        }finally{pool.shutdownNow();}
        assertEquals(2,numero("select count(*) from p1_examen"));
        assertEquals(90,numero("select puntaje_examen::int from p1_postulacion where id=1"));
        assertEquals(86,numero("select puntaje_final::int from p1_postulacion where id=1"));
        assertEquals(2,numero("select count(*) from p1_auditoria where accion='REGEXA'"));
    }
    @Test void reintentosConcurrentesDelMismoMensajeRecuperanElMismoResultado()throws Exception{
        Orden persona=new Orden("INSPER",List.of("123","Ana","Lopez","Estudiante","33333","77777","ana@example.com"));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var inicio=new java.util.concurrent.CountDownLatch(1);
        try{
            var a=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("mismo-correo","a@example.com",persona);});
            var b=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("mismo-correo","a@example.com",persona);});
            inicio.countDown();var primero=a.get(15,java.util.concurrent.TimeUnit.SECONDS);var segundo=b.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(primero.exito());assertEquals(primero,segundo);
        }finally{pool.shutdownNow();}
        assertEquals(1,numero("select count(*) from persona"));
        assertEquals(1,numero("select count(*) from p1_auditoria where accion='INSPER'"));
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
