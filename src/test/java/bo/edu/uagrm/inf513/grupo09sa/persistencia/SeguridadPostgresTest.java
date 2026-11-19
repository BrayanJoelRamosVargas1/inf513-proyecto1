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
    @Test void primerDecanoDebeQuedarActivoParaPoderAdministrarElSistema()throws Exception{
        var parametros=new java.util.ArrayList<>(alta().parametros());
        parametros.set(6,"false");
        Resultado rechazado=procesador.procesar("arranque-inactivo","admin@example.com",new Orden("INSUSU",parametros));
        assertFalse(rechazado.exito());
        assertTrue(rechazado.cuerpo().contains("El primer DECANO debe quedar activo."),rechazado.cuerpo());
        assertEquals(0,usuarios());
        assertEquals(0,numero("select count(*) from p1_correo_control where token_hash is not null"));

        String token=codigo(procesador.procesar("arranque-activo","admin@example.com",alta()));
        assertTrue(confirmar("confirmar-arranque","admin@example.com",token).exito());
        assertTrue(repo.autorizado("admin@example.com","DECANO"));
        assertTrue(ordenar("admin@example.com","INSUSU","2","Eva","Prueba","EVALUADOR",
                "70000001","eva@example.com","false").exito());
        assertEquals(1,numero("select count(*) from p1_usuario where not activo"));
    }
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
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("exceso","luis@example.com","INSPOST[]",List.of(pdf)));
        assertEquals(4,numero("select count(*) from p1_adjunto_correo"));
        assertEquals(1,numero("select count(*) from p1_correo_control where mensaje_id='exceso' and asunto like 'ADJUNTO_RECHAZADO:%'"),"El rechazo es durable y no bloquea la bandeja");
        assertEquals(0,numero("select count(*) from p1_adjunto_correo where mensaje_id='exceso'"));
        procesador.procesar("cupo-0","luis@example.com",new Orden("INSPOST",List.of("1","2","ADJUNTO")));
        repo.confirmarRespuesta("cupo-0");
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("exceso-2","luis@example.com","INSPOST[]",List.of(pdf)),"La confirmación pendiente conserva su PDF y el exceso recibe rechazo");
        repo.confirmarRespuesta("cupo-1");
        assertEquals(RepositorioPostgres.Admision.DIFERIDO,repo.registrarEntrada("reintento","luis@example.com","INSPOST[]",List.of(pdf)));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='cupo-0'"));
        assertEquals(4,numero("select count(*) from p1_adjunto_correo"));
    }
    bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf pdfDeDosMiB(){
        byte[] contenido=new byte[2*1024*1024];
        System.arraycopy("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,contenido,0,8);
        return new bo.edu.uagrm.inf513.grupo09sa.email.AdjuntoPdf("expediente.pdf",contenido);
    }
    List<String> llenarCupoConConfirmacionesPdf()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSCAR","Segundo cargo","Facultad","true").exito());
        var tokens=new java.util.ArrayList<String>();var pdf=pdfDeDosMiB();
        for(int i=0;i<4;i++){
            String id="pdf-expira-"+i;
            repo.registrarEntrada(id,"luis@example.com","INSPOST[\"1\",\"2\",\"ADJUNTO\"]",List.of(pdf));
            tokens.add(codigo(procesador.procesar(id,"luis@example.com",new Orden("INSPOST",List.of("1","2","ADJUNTO")))));
            repo.confirmarRespuesta(id);
        }
        assertEquals(4,numero("select count(*) from p1_adjunto_correo"));
        return tokens;
    }
    @Test void pdfVencidosLiberanCupoSinBorrarExpedientesConfirmados()throws Exception{
        var tokens=llenarCupoConConfirmacionesPdf();
        ejecutarSql("update p1_correo_control set vence_en=now()-interval '1 minute' where mensaje_id like 'pdf-expira-%'");
        repo.registrarEntrada("nuevo-tras-vencer","luis@example.com","INSPOST[]",List.of(pdfDeDosMiB()));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='nuevo-tras-vencer'"),"Los códigos vencidos no deben bloquear permanentemente el cupo de PDF");
        assertEquals(0,numero("select count(*) from p1_adjunto_correo where mensaje_id like 'pdf-expira-%'"));
        assertEquals(4,numero("select count(*) from p1_correo_control where token_hash is not null and mensaje_id like 'pdf-expira-%'"),"Se conserva el registro de las confirmaciones");
        assertEquals(1,numero("select count(*) from p1_expediente"),"La limpieza solo afecta los adjuntos temporales");
        assertFalse(confirmar("vencida-posterior","luis@example.com",tokens.get(0)).exito());
        assertEquals(1,numero("select count(*) from p1_postulacion"));
    }
    @Test void limpiezaConservaPdfSiLaConfirmacionValidaLlegoATiempoPeroEstaDiferida()throws Exception{
        var tokens=llenarCupoConConfirmacionesPdf();
        ejecutarSql("update p1_correo_control set vence_en=now()-interval '1 minute' where mensaje_id like 'pdf-expira-%'");
        repo.registrarEntrada("confirmacion-pdf-diferida","LUIS@example.com"," confirmar [ \u2003\""+tokens.get(0)+"\"\u2003 ] ");
        ejecutarSql("update p1_correo_control set creado_en=now()-interval '2 minutes' where mensaje_id='confirmacion-pdf-diferida'");
        repo.registrarEntrada("nuevo-con-cola","luis@example.com","INSPOST[]",List.of(pdfDeDosMiB()));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='pdf-expira-0'"));
        assertEquals(0,numero("select count(*) from p1_adjunto_correo where mensaje_id in('pdf-expira-1','pdf-expira-2','pdf-expira-3')"));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='nuevo-con-cola'"));
        assertTrue(confirmar("confirmacion-pdf-diferida","luis@example.com",tokens.get(0)).exito());
        assertEquals(1,numero("select count(*) from p1_expediente where postulacion_id=2 and octet_length(contenido)=2097152"));
    }
    @Test void confirmacionesFalsasOAtrasadasNoRetienenPdfVencidos()throws Exception{
        var tokens=llenarCupoConConfirmacionesPdf();
        ejecutarSql("update p1_correo_control set vence_en=now()-interval '1 minute' where mensaje_id like 'pdf-expira-%'");
        repo.registrarEntrada("falsa-cuenta","intruso@example.com","CONFIRMAR[\""+tokens.get(0)+"\"]");
        repo.registrarEntrada("llego-tarde","luis@example.com","CONFIRMAR[\""+tokens.get(1)+"\"]");
        repo.registrarEntrada("codigo-incorrecto","luis@example.com","CONFIRMAR[\""+"X".repeat(43)+"\"]");
        repo.registrarEntrada("parametro-extra","luis@example.com","CONFIRMAR[\""+tokens.get(2)+"\",\"extra\"]");
        repo.registrarEntrada("comando-distinto","luis@example.com","LISPER[\""+tokens.get(3)+"\"]");
        ejecutarSql("update p1_correo_control set creado_en=now()-interval '2 minutes' where mensaje_id in('falsa-cuenta','codigo-incorrecto','parametro-extra','comando-distinto')");
        repo.registrarEntrada("nuevo-sin-retencion-falsa","luis@example.com","INSPOST[]",List.of(pdfDeDosMiB()));
        assertEquals(0,numero("select count(*) from p1_adjunto_correo where mensaje_id like 'pdf-expira-%'"));
        assertEquals(1,numero("select count(*) from p1_adjunto_correo where mensaje_id='nuevo-sin-retencion-falsa'"));
        assertFalse(confirmar("falsa-cuenta","intruso@example.com",tokens.get(0)).exito());
        assertFalse(confirmar("llego-tarde","luis@example.com",tokens.get(1)).exito());
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
        assertTrue(ordenar("eva@example.com","REGMER","0001","Experiencia","80","Documento revisado").exito());
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select actor,detalle,entidad_id from p1_auditoria where accion='REGMER'")){
            assertTrue(r.next());assertEquals("eva@example.com",r.getString(1));
            assertEquals("1",r.getString(3));
            var d=new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getString(2));
            assertEquals("80",d.path("parametros").get(2).asText());
            assertEquals(0,d.path("anterior").path("puntaje_meritos").asInt());
            assertEquals(80,d.path("posterior").path("puntaje_meritos").asInt());
            assertEquals(32,d.path("posterior").path("puntaje_final").asInt());assertFalse(r.next());
        }
        String historial=ordenar("admin@example.com","AUDCAL","REGMER","1").cuerpo();
        assertTrue(historial.contains("Documento revisado"));assertTrue(historial.contains("posterior"));
        ejecutarSql("update p1_auditoria set entidad_id='+0001' where accion='REGMER'");
        String historico=ordenar("admin@example.com","AUDCAL","REGMER","0001").cuerpo();
        assertTrue(historico.contains("Documento revisado"));
    }
    @Test void auditoriaConsultaAltasPorLaReferenciaQueGuarda()throws Exception{
        iniciarFlujo();
        String nombre="A".repeat(160);
        Resultado cargo=ordenar("admin@example.com","INSCAR",nombre,"Facultad","true");
        assertTrue(cargo.exito(),cargo.cuerpo());
        assertEquals(2,numero("select count(*) from p1_cargo"));
        Resultado altaCargo=ordenar("admin@example.com","AUDCAL","INSCAR",nombre);
        assertTrue(altaCargo.exito(),altaCargo.cuerpo());
        assertTrue(altaCargo.cuerpo().contains("admin@example.com | INSCAR"));
        assertTrue(ordenar("admin@example.com","AUDCAL","INSCON","AUX").cuerpo().contains("admin@example.com | INSCON"));
        assertFalse(ordenar("luis@example.com","AUDCAL","INSCAR",nombre).exito());
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
        ejecutarSql("update p1_correo_control set vence_en=now()-interval '1 minute'");
        assertFalse(confirmar("c","admin@example.com",token).exito());assertEquals(0,usuarios());
    }
    @Test void falloDeBaseRevierteNegocioYConsumoDelCodigo()throws Exception{
        String token=codigo(procesador.procesar("rollback","admin@example.com",alta()));
        assertThrows(SQLException.class,()->repo.confirmarOrden("c","admin@example.com",token,(id,o)->{
            repo.ejecutarIdempotente(id,"admin@example.com",o);throw new SQLException("Corte simulado","08006");
        }));
        assertEquals(0,usuarios());assertEquals(0,numero("select count(*) from p1_correo_control where usado"));
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
        ejecutarSql("update p1_correo_control set vence_en=now()-interval '1 hour'");
        assertTrue(confirmar("confirmacion-cola","admin@example.com",token).exito());assertEquals(1,usuarios());
    }
    @Test void migracionRepetidaConservaLasOrdenesDiferidas()throws Exception{
        repo.registrarEntrada("conservar","a@example.com","AYUDA[]");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute(Files.readString(Path.of("database/schema.sql")));}
        assertEquals(1,repo.promoverDiferidos(25));assertEquals("conservar",repo.correosPendientes(25).get(0).id());
    }
    void habilitarYCalificar(String post,String nota)throws Exception{
        assertTrue(ordenar("eva@example.com","EVAREQ",post,"Identidad","true","Verificado").exito());
        assertTrue(ordenar("admin@example.com","HABPOS",post,"HABILITADO","Cumple").exito());
        assertTrue(ordenar("eva@example.com","REGMER",post,"Experiencia",nota,"Verificado").exito());
        assertTrue(ordenar("eva@example.com","REGEXA",post,nota,"Verificado").exito());
    }
    void segundoPostulante()throws Exception{
        assertTrue(ordenar("admin@example.com","INSUSU","4","Carla","Perez","POSTULANTE","70000003","carla@example.com","true").exito());
        assertTrue(ordenar("carla@example.com","INSPOST","1","1","https://example.com/carla.pdf").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","2","2","MERITOS_Y_EXAMEN").exito());
    }
    @Test void postulanteNoConsultaSolicitudesAjenasNiConSqlNiConIdConocido()throws Exception{
        iniciarFlujo();segundoPostulante();
        var todas=ordenar("luis@example.com","LISPOST","*");
        assertTrue(todas.exito());assertTrue(todas.cuerpo().contains("1 | AUX |"));assertFalse(todas.cuerpo().contains("2 | AUX |"));
        for(String filtro:List.of("2","' OR '1'='1","%","_")) {
            var r=ordenar("luis@example.com","LISPOST",filtro);
            assertTrue(r.exito());assertEquals("Sin resultados.",r.cuerpo());
        }
        var carla=ordenar("carla@example.com","LISPOST","*");
        assertTrue(carla.exito());assertTrue(carla.cuerpo().contains("2 | AUX |"));assertFalse(carla.cuerpo().contains("1 | AUX |"));
        assertFalse(procesador.procesar("leer-roles-ajenos","luis@example.com",new Orden("LISUSU",List.of("*"))).exito());
        assertEquals(2,numero("select count(*) from p1_postulacion"));
    }
    @Test void evaluadorNoCalificaOtraPostulacionAunqueConozcaSuId()throws Exception{
        iniciarFlujo();segundoPostulante();
        ejecutarSql("delete from p1_asignacion_evaluador where postulacion_id=2");
        assertFalse(ordenar("eva@example.com","EVAREQ","2","Identidad","true","Intento sin asignación").exito());
        assertEquals(0,numero("select count(*) from p1_evaluacion_requisito where postulacion_id=2"));
        // Estado elegible de ensayo: el rechazo debe depender de la asignación, no solo del estado.
        ejecutarSql("insert into p1_evaluacion_requisito(postulacion_id,requisito,cumple,evaluador_correo) values(2,'Identidad',true,'otro@example.com')");
        ejecutarSql("update p1_postulacion set estado='HABILITADO' where id=2");
        assertFalse(ordenar("eva@example.com","REGMER","2","Experiencia","100","Intento sin asignación").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","2","100","Intento sin asignación").exito());
        assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1","teoria","100").exito());
        assertFalse(ordenar("eva@example.com","REGAREA","2","teoria","100","Intento sin asignación").exito());
        assertEquals(0,numero("select count(*) from p1_merito where postulacion_id=2"));
        assertEquals(0,numero("select count(*) from p1_examen where postulacion_id=2"));
        assertEquals(0,numero("select puntaje_final::int from p1_postulacion where id=2"));
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Asignación válida").exito());
    }
    @Test void dosConfirmacionesSimultaneasNoReutilizanUnCodigoNiDuplicanLaEscritura()throws Exception{
        String token=codigo(procesador.procesar("alta-competida","admin@example.com",alta()));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var inicio=new java.util.concurrent.CountDownLatch(1);
        try {
            var a=pool.submit(()->{inicio.await();return confirmar("confirmacion-a","admin@example.com",token);});
            var b=pool.submit(()->{inicio.await();return confirmar("confirmacion-b","admin@example.com",token);});
            inicio.countDown();
            var ra=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var rb=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(ra.exito(),rb.exito(),"Solo una confirmación distinta puede consumir el código");
            assertEquals(1,usuarios());assertEquals(1,numero("select count(*) from p1_auditoria where accion='INSUSU'"));
            assertFalse(confirmar("repeticion-tercera","admin@example.com",token).exito());
            assertTrue(confirmar(ra.exito()?"confirmacion-a":"confirmacion-b","admin@example.com",token).exito());
            assertEquals(1,usuarios());
        } finally {pool.shutdownNow();}
    }
    @Test void referenciaDeExpedienteNoProvocaDescargaDeUnaDireccionInterna()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSCAR","Otro cargo","Facultad","true").exito());
        try(var servidor=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
            servidor.setSoTimeout(250);
            String referencia="http://127.0.0.1:"+servidor.getLocalPort()+"/dato-interno";
            var r=ordenar("luis@example.com","INSPOST","1","2",referencia);
            assertTrue(r.exito(),r.cuerpo());assertTrue(r.cuerpo().contains("referencia externa"));
            assertThrows(java.net.SocketTimeoutException.class,()->{try(var socket=servidor.accept()){fail("El servicio no debe descargar referencias de expediente");}});
            try(var c=ds.getConnection();var s=c.prepareStatement("select ruta_archivo,contenido from p1_expediente where postulacion_id=2");var filas=s.executeQuery()) {
                assertTrue(filas.next());assertEquals(referencia,filas.getString(1));assertNull(filas.getBytes(2));
            }
        }
    }
    @Test void categoriasTienenRankingYCuposIndependientes()throws Exception{
        iniciarFlujo();ejecutarSql("update p1_convocatoria set plazas=2");
        assertTrue(ordenar("admin@example.com","INSCAR","Redes","Facultad","true").exito());
        assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","ASIOFE","1","2","1").exito());
        assertTrue(ordenar("luis@example.com","INSPOST","1","2","https://example.com/redes.pdf").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","2","2","MERITOS_Y_EXAMEN").exito());
        habilitarYCalificar("1","80");habilitarYCalificar("2","80");
        Resultado ranking=ordenar("admin@example.com","GENRAN","1");
        assertTrue(ranking.exito());assertTrue(ranking.cuerpo().contains("Redes"));assertTrue(ranking.cuerpo().contains("Auxiliar"));
        assertFalse(ranking.cuerpo().contains("EMPATE_PENDIENTE"));
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","2","true").exito());
        assertEquals(2,numero("select count(*) from p1_postulacion where plaza_asignada"));
        Resultado reporte=ordenar("admin@example.com","REPEST","1");
        assertTrue(reporte.exito());assertTrue(reporte.cuerpo().contains("aporte_meritos_40"));assertTrue(reporte.cuerpo().contains("vacantes"));
        assertTrue(reporte.cuerpo().contains("GANADOR"));assertTrue(reporte.cuerpo().contains("RANKING Y EMPATES"));
        assertTrue(reporte.cuerpo().lines().anyMatch("1 | Auxiliar | 1 | 1 | 1 | 0"::equals),reporte.cuerpo());
        assertTrue(reporte.cuerpo().lines().anyMatch("2 | Redes | 1 | 1 | 1 | 0"::equals),reporte.cuerpo());
    }
    @Test void reporteUsaUnSoloCupoGlobalYRespetaLasOfertasCuandoSeConfiguran()throws Exception{
        iniciarFlujo();
        assertTrue(ordenar("admin@example.com","INSCAR","Redes","Facultad","true").exito());
        assertTrue(ordenar("luis@example.com","INSPOST","1","2","https://example.com/redes.pdf").exito());

        String global=ordenar("admin@example.com","REPEST","1").cuerpo();
        assertTrue(global.lines().anyMatch("NULL | Cupo global | 1 | 2 | 0 | 1"::equals),global);

        assertTrue(ordenar("admin@example.com","ASIOFE","1","2","1").exito());
        String porCargo=ordenar("admin@example.com","REPEST","1").cuerpo();
        assertTrue(porCargo.lines().anyMatch("2 | Redes | 1 | 1 | 0 | 1"::equals),porCargo);
        assertTrue(porCargo.lines().anyMatch(fila -> fila.startsWith("1 | Auxiliar | 1 | ") &&
                fila.endsWith(" | f | 0 | 0")),porCargo);
    }
    @Test void reporteGrandeConservaTodasLasSeccionesYElCupoReal()throws Exception{
        iniciarFlujo();
        ejecutarSql("""
            update p1_cargo set nombre=repeat('A',155) where id=1;
            insert into p1_usuario(registro,nombres,apellidos,rol,correo)
                select n::text,repeat('N',95),repeat('P',115),'POSTULANTE','p'||n||'@example.com'
                from generate_series(10,239) n;
            insert into p1_postulacion(convocatoria_id,postulante_id,cargo_id,estado,
                    puntaje_meritos,puntaje_examen,puntaje_final)
                select 1,id,1,'HABILITADO',80,85,83 from p1_usuario where id>3;
            insert into p1_asignacion_evaluador(postulacion_id,evaluador_id,tipo)
                select id,2,'MERITOS_Y_EXAMEN' from p1_postulacion where id>1;
            insert into p1_evaluacion_requisito(postulacion_id,requisito,cumple,evaluador_correo)
                select id,'Identidad',true,'eva@example.com' from p1_postulacion where id>1;
            insert into p1_merito(postulacion_id,criterio,puntaje,evaluador_correo)
                select id,'Experiencia',80,'eva@example.com' from p1_postulacion where id>1;
            insert into p1_examen(postulacion_id,puntaje,evaluador_correo)
                select id,85,'eva@example.com' from p1_postulacion where id>1;
            """);

        Resultado respuesta=ordenar("admin@example.com","REPEST","1");
        assertTrue(respuesta.exito());
        String reporte=respuesta.cuerpo();
        for(String seccion:List.of("RESUMEN POR ESTADO","CONSOLIDADO POR CATEGORÍA",
                "ÁREAS Y PONDERACIONES","RANKING Y EMPATES","PLAZAS Y VACANTES")){
            assertTrue(reporte.lines().anyMatch(seccion::equals),"Falta la sección "+seccion);
        }
        assertTrue(reporte.lines().anyMatch("HABILITADO | 230 | 83.00"::equals));
        assertTrue(reporte.lines().anyMatch("NULL | Cupo global | 1 | 231 | 0 | 1"::equals));
        assertTrue(reporte.contains("... se omitieron filas por el límite de salida."));
        assertTrue(reporte.length()<=50_000);
    }

    @Test void configuracionDeCuposConcurrenteNoSuperaElTotal()throws Exception{
        iniciarFlujo();assertTrue(ordenar("admin@example.com","INSCAR","Redes","Facultad","true").exito());
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var inicio=new java.util.concurrent.CountDownLatch(1);
        try{
            var a=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("oferta-a","admin@example.com",new Orden("ASIOFE",List.of("1","1","1")));});
            var b=pool.submit(()->{inicio.await();return repo.ejecutarIdempotente("oferta-b","admin@example.com",new Orden("ASIOFE",List.of("1","2","1")));});
            inicio.countDown();var ra=a.get(15,java.util.concurrent.TimeUnit.SECONDS);var rb=b.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(ra.exito(),rb.exito());assertEquals(1,numero("select sum(plazas)::int from p1_oferta"));
        }finally{pool.shutdownNow();}
    }
    @Test void empateEnElCorteNoSeResuelvePorIdYExigeDecisionAuditada()throws Exception{
        iniciarFlujo();segundoPostulante();habilitarYCalificar("1","80");habilitarYCalificar("2","80");
        assertTrue(ordenar("admin@example.com","GENRAN","1").cuerpo().contains("EMPATE_PENDIENTE"));
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").cuerpo().contains("Empate pendiente"));
        assertEquals(0,numero("select count(*) from p1_postulacion where plaza_asignada"));
        assertTrue(ordenar("admin@example.com","DESEMP","1","1","2,1","Acta de autoridad ficticia número 1").exito());
        assertFalse(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","2","true").exito());
        assertEquals(1,numero("select count(*) from p1_auditoria where accion='DESEMP' and detalle like '%Acta de autoridad%'"));
    }
    @Test void rankingDejaPendienteATodoElEmpateSiSeSumaOtroCandidato()throws Exception{
        iniciarFlujo();segundoPostulante();habilitarYCalificar("1","80");habilitarYCalificar("2","80");
        assertTrue(ordenar("admin@example.com","DESEMP","1","1","2,1","Acta para dos candidatos").exito());
        assertTrue(ordenar("admin@example.com","GENRAN","1").cuerpo().contains("RESOLUCION_REGISTRADA"));

        assertTrue(ordenar("admin@example.com","INSUSU","7","Mario","Prueba","POSTULANTE","70000007","mario@example.com","true").exito());
        assertTrue(ordenar("mario@example.com","INSPOST","1","1","https://example.com/mario.pdf").exito());
        assertTrue(ordenar("admin@example.com","ASIEVA","3","2","MERITOS_Y_EXAMEN").exito());
        habilitarYCalificar("3","80");

        String ranking=ordenar("admin@example.com","GENRAN","1").cuerpo();
        assertEquals(3,ranking.lines().filter(fila->fila.endsWith(" | 1 | EMPATE_PENDIENTE | NULL")).count(),ranking);
        assertTrue(ordenar("admin@example.com","ASIPLA","2","true").cuerpo().contains("Empate pendiente"));
        assertTrue(ordenar("admin@example.com","DESEMP","1","1","3,2,1","Nueva acta para tres candidatos").exito());
        String actualizado=ordenar("admin@example.com","GENRAN","1").cuerpo();
        assertEquals(3,actualizado.lines().filter(fila->fila.contains("RESOLUCION_REGISTRADA")).count(),actualizado);
        assertTrue(ordenar("admin@example.com","ASIPLA","3","true").exito());
    }
    @Test void corregirUnaNotaInvalidaLaResolucionDelEmpateAunqueConserveElPuntaje()throws Exception{
        iniciarFlujo();segundoPostulante();habilitarYCalificar("1","80");habilitarYCalificar("2","80");
        assertTrue(ordenar("admin@example.com","DESEMP","1","1","2,1","Acta ficticia para prueba de versión").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","80","Corrección documentada").exito());
        assertFalse(ordenar("admin@example.com","ASIPLA","2","true").exito());
        assertTrue(ordenar("admin@example.com","DESEMP","1","1","2,1","Nueva acta ficticia después de corregir").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","2","true").exito());
    }
    @Test void examenPonderadoExigeTodasLasAreasYCienPorCiento()throws Exception{
        iniciarFlujo();assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1","Teoría","30").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Revisado").exito());
        assertFalse(ordenar("eva@example.com","REGAREA","1","teoría","50","Examen").exito());
        assertFalse(ordenar("admin@example.com","CFGARE","1","1","Práctica","80").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1","Práctica","70").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","1","100","Evitar ponderaciones").exito());
        assertTrue(ordenar("eva@example.com","REGAREA","1","TEORÍA","50","Examen").exito());
        assertFalse(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("eva@example.com","REGAREA","1","práctica","90","Examen").exito());
        assertEquals(7880,numero("select (puntaje_final*100)::int from p1_postulacion where id=1"));
        assertFalse(ordenar("admin@example.com","CFGARE","1","1","Práctica","60").exito());
        assertTrue(ordenar("admin@example.com","ASIPLA","1","true").exito());
        assertTrue(ordenar("admin@example.com","REPEST","1").cuerpo().contains("práctica"));
    }
    @Test void asignacionDeMeritosYExamenRespetaLaTareaIndicada()throws Exception{
        iniciarFlujo();
        ejecutarSql("delete from p1_asignacion_evaluador");
        assertTrue(ordenar("admin@example.com","ASIEVA","1","2","MERITOS").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        assertFalse(ordenar("eva@example.com","REGEXA","1","95","Sin permiso de examen").exito());
        assertTrue(ordenar("eva@example.com","REGMER","1","Experiencia","80","Asignación de méritos").exito());

        ejecutarSql("delete from p1_asignacion_evaluador");
        assertTrue(ordenar("admin@example.com","ASIEVA","1","2","EXAMEN").exito());
        assertFalse(ordenar("eva@example.com","REGMER","1","Experiencia","95","Sin permiso de méritos").exito());
        assertTrue(ordenar("eva@example.com","REGEXA","1","85","Asignación de examen").exito());
        assertEquals(1,numero("select count(*) from p1_merito"));
        assertEquals(1,numero("select count(*) from p1_examen"));
        assertEquals(2,numero("select count(*) from p1_auditoria where accion in ('REGMER','REGEXA')"));
    }

    @Test void asignacionAceptaUnAreaLargaConfiguradaYRechazaUnNombreInventado()throws Exception{
        iniciarFlujo();
        String area="diagnóstico y resolución de problemas de infraestructura de comunicaciones";
        assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1",area,"100").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        ejecutarSql("delete from p1_asignacion_evaluador");
        Resultado alta=ordenar("admin@example.com","ASIEVA","1","2",area);
        assertTrue(alta.exito(),alta.cuerpo());
        assertFalse(ordenar("admin@example.com","ASIEVA","1","2","área equivocada").exito());
        assertTrue(ordenar("eva@example.com","REGAREA","1",area,"80","Evaluación realizada").exito());
        assertEquals(1,numero("select count(*) from p1_asignacion_evaluador"));
        assertEquals(80,numero("select puntaje_examen::int from p1_postulacion where id=1"));
    }

    @Test void nuevasOperacionesNoPermitenEscalarRolesNiCambiarElAreaAsignada()throws Exception{
        iniciarFlujo();
        assertFalse(ordenar("eva@example.com","ASIOFE","1","1","1").exito());
        assertFalse(ordenar("luis@example.com","LISCFG","1").exito());
        assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1","teoría","50").exito());
        assertTrue(ordenar("admin@example.com","CFGARE","1","1","práctica","50").exito());
        assertTrue(ordenar("eva@example.com","EVAREQ","1","Identidad","true","Vigente").exito());
        assertTrue(ordenar("admin@example.com","HABPOS","1","HABILITADO","Cumple").exito());
        ejecutarSql("update p1_asignacion_evaluador set tipo='teoría'");
        assertFalse(ordenar("eva@example.com","REGMER","1","Experiencia","99","Solo asignada al área").exito());
        assertFalse(ordenar("eva@example.com","REGAREA","1","práctica","99","No asignada").exito());
        assertTrue(ordenar("eva@example.com","REGAREA","1","teoría","80","Asignada").exito());
        assertFalse(ordenar("eva@example.com","DESEMP","1","1","1,2","Decisión no autorizada").exito());
    }
    @Test void configurarOfertasImpidePostularACargosNoOfertados()throws Exception{
        iniciarFlujo();assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        assertTrue(ordenar("admin@example.com","INSCAR","Redes","Facultad","true").exito());
        assertFalse(ordenar("luis@example.com","INSPOST","1","2","https://example.com/redes.pdf").exito());
        assertEquals(1,numero("select count(*) from p1_postulacion"));
        assertFalse(ordenar("admin@example.com","ASIOFE","1","2","1").exito());
    }
    @Test void nombresDeAreaConSqlSeConservanComoDatos()throws Exception{
        iniciarFlujo();assertTrue(ordenar("admin@example.com","ASIOFE","1","1","1").exito());
        String area="redes'); drop table p1_usuario; --";
        assertTrue(ordenar("admin@example.com","CFGARE","1","1",area,"100").exito());
        assertEquals(3,usuarios());
        try(var c=ds.getConnection();var q=c.prepareStatement("select area from p1_area_examen" );var r=q.executeQuery()){
            assertTrue(r.next());assertEquals(area,r.getString(1));
        }
        assertFalse(ordenar("admin@example.com","ASIOFE","1","1","1;drop table persona").exito());
        assertFalse(ordenar("admin@example.com","DESEMP","1","1","1,1","Repetición no permitida").exito());
    }
}
