package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.presentacion.ControladorCorreo;
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
        repo=new RepositorioPostgres(ds,"imap");repo.marcarServicioInicializado("imap");processor=new ProcesadorOrden(new CatalogoComandos(),repo,"admin@example.com");
    }
    @AfterEach void limpiar()throws Exception{if(schema!=null)sql("drop schema "+schema+" cascade");}
    void sql(String sql)throws Exception{try(var c=ds.getConnection();var s=c.createStatement()){s.execute(sql);}}
    int cantidad(String tabla)throws Exception{try(var c=ds.getConnection();var s=c.createStatement();var rs=s.executeQuery("select count(*) from "+tabla)){rs.next();return rs.getInt(1);}}
    void ciclo(CorreoFalso mail)throws Exception{ciclo(mail,AntivirusPdf.desdeEntorno());}
    void ciclo(CorreoFalso mail,InspectorPdf inspector)throws Exception{new ControladorCorreo(cfg.inboundProtocol(),cfg.maxMessagesPerCycle(),cfg.retryMaxSeconds(),mail,repo,processor,new AnalizadorOrden(),inspector).ejecutarCiclo();}
    AdjuntoPdf pdf(){return new AdjuntoPdf("expediente.pdf","%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    void postulante()throws Exception{sql("insert into usuario(registro,nombres,apellidos,rol,telefono,correo,activo) values('3','Luis','Prueba','POSTULANTE','70000002','luis@example.com',true)");}
    CorreoFalso correoPdf(){var mail=new CorreoFalso();mail.entrada.add(new MensajeEntrada("pdf-av","luis@example.com","INSPOST[\"1\",\"1\",\"ADJUNTO\"]",List.of(pdf())));return mail;}
    @Test void pdfAprobadoSeConservaAntesDePedirConfirmacion()throws Exception{
        postulante();var mail=correoPdf();var inspecciones=new java.util.concurrent.atomic.AtomicInteger();
        ciclo(mail,a->{inspecciones.incrementAndGet();return true;});ciclo(mail,a->{fail("Un correo conocido no se vuelve a analizar");return false;});
        assertEquals(1,inspecciones.get());assertEquals(1,cantidad("adjunto_correo"));assertEquals(1,cantidad("correo_control where token_hash is not null"));assertEquals(0,cantidad("postulacion"));
    }
    @Test void pdfNoAprobadoSeRespondeSinAlmacenarNiConfirmar()throws Exception{
        postulante();var mail=correoPdf();ciclo(mail,a->false);ciclo(mail,a->false);
        assertEquals(0,cantidad("adjunto_correo"));assertEquals(0,cantidad("correo_control where token_hash is not null"));
        assertTrue(mail.detalle.get("pdf-av").cuerpo().contains("antivirus no aprobó"));assertTrue(repo.correoProcesado("pdf-av"));
    }
    @Test void motorAntivirusInaccesibleNoApruebaNiBloqueaOtrosCorreos()throws Exception{
        postulante();var mail=correoPdf();mail.entrada.add(new MensajeEntrada("siguiente-av","otro@example.com","AYUDA[]"));
        InspectorPdf caido=a->{throw new java.io.IOException("Fallo de motor simulado");};ciclo(mail,caido);ciclo(mail,caido);
        assertEquals(0,cantidad("adjunto_correo"));assertEquals(0,cantidad("correo_control where token_hash is not null"));
        assertTrue(mail.detalle.get("pdf-av").cuerpo().contains("no estuvo disponible"));assertTrue(repo.correoProcesado("siguiente-av"));
    }
    @Test void remitenteSinRolNoConsumeElProcesoAntivirus()throws Exception{
        var mail=correoPdf();ciclo(mail,a->{fail("No debe analizar un PDF sin rol");return false;});ciclo(mail,a->true);
        assertEquals(0,cantidad("adjunto_correo"));assertTrue(mail.detalle.get("pdf-av").cuerpo().contains("rol requerido"));
    }
    @Test void cupoPdfAgotadoNoRetieneLaPrimeraPaginaDeLaBandeja()throws Exception{
        postulante();byte[] bytes=new byte[2*1024*1024];System.arraycopy("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,bytes,0,5);
        var grande=new AdjuntoPdf("grande.pdf",bytes);
        for(int i=0;i<4;i++)repo.registrarEntrada("almacenado-"+i,"luis@example.com","INSPOST[]",List.of(grande));
        var mail=new CorreoFalso();mail.limite=2;
        mail.entrada.add(new MensajeEntrada("exceso-a","luis@example.com","INSPOST[]",List.of(grande)));
        mail.entrada.add(new MensajeEntrada("exceso-b","luis@example.com","INSPOST[]",List.of(grande)));
        mail.entrada.add(new MensajeEntrada("legitimo-detras","otro@example.com","AYUDA[]"));
        for(int i=0;i<4;i++)ciclo(mail,a->true);
        assertTrue(repo.correoProcesado("legitimo-detras"));assertTrue(mail.detalle.get("exceso-a").cuerpo().contains("cupo temporal"));
        assertEquals(4,cantidad("adjunto_correo"));
    }
    Respuesta enviar(CorreoFalso mail,String remitente,String asunto,List<AdjuntoPdf> adjuntos)throws Exception{
        String id="ensayo-"+UUID.randomUUID();mail.entrada.add(new MensajeEntrada(id,remitente,asunto,adjuntos));
        ciclo(mail,a->true);ciclo(mail,a->true);assertTrue(mail.detalle.containsKey(id),asunto);return mail.detalle.get(id);
    }
    Respuesta orden(CorreoFalso mail,String remitente,String comando,String... parametros)throws Exception{
        String asunto=comando+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(parametros));
        Respuesta r=enviar(mail,remitente,asunto,comando.equals("INSPOST")?List.of(pdf()):List.of());
        if(r.asunto().startsWith("CONFIRMACIÓN REQUERIDA")){
            var token=java.util.regex.Pattern.compile("CONFIRMAR\\[\"([A-Za-z0-9_-]{43})\"\\]").matcher(r.cuerpo());assertTrue(token.find(),r.cuerpo());
            r=enviar(mail,remitente,"CONFIRMAR[\""+token.group(1)+"\"]",List.of());
        }
        assertEquals("ÉXITO - Orden procesada",r.asunto(),r.cuerpo());return r;
    }
    @Test void ochoCasosPorCicloCompletoConCuatroRolesYConfirmaciones()throws Exception{
        var mail=new CorreoFalso();
        orden(mail,"admin@example.com","INSUSU","1","Ana","Prueba","DECANO","70000000","admin@example.com","true");
        orden(mail,"admin@example.com","INSUSU","2","Eva","Prueba","EVALUADOR","70000001","eva@example.com","true");
        orden(mail,"admin@example.com","INSUSU","3","Luis","Prueba","POSTULANTE","70000002","luis@example.com","true");
        orden(mail,"admin@example.com","INSUSU","4","Vice","Prueba","VICEDECANO","70000003","vice@example.com","true");
        var hoy=java.time.LocalDate.now();
        orden(mail,"admin@example.com","INSCON","ENSAYO","Auxiliaturas",hoy.minusDays(1).toString(),hoy.plusDays(1).toString(),"ABIERTA","1");
        orden(mail,"vice@example.com","INSCAR","Auxiliar","Facultad","true");
        orden(mail,"admin@example.com","ASIOFE","1","1","1");
        orden(mail,"luis@example.com","INSPOST","1","1","ADJUNTO");
        orden(mail,"admin@example.com","ASIEVA","1","2","MERITOS_Y_EXAMEN");
        orden(mail,"eva@example.com","EVAREQ","1","Identidad","true","Verificado");
        orden(mail,"vice@example.com","HABPOS","1","HABILITADO","Cumple");
        orden(mail,"eva@example.com","REGMER","1","Experiencia","80","Verificado");
        orden(mail,"eva@example.com","REGEXA","1","85","Verificado");
        assertTrue(orden(mail,"admin@example.com","GENRAN","1").cuerpo().contains("83.00"));
        orden(mail,"admin@example.com","ASIPLA","1","true");
        assertTrue(orden(mail,"vice@example.com","AUDCAL","ASIPLA","1").cuerpo().contains("admin@example.com"));
        String reporte=orden(mail,"vice@example.com","REPEST","1").cuerpo();
        assertTrue(reporte.contains("GANADOR"));
        assertTrue(reporte.lines().anyMatch("1 | Auxiliar | 1 | 1 | 1 | 0"::equals),reporte);
        assertTrue(orden(mail,"luis@example.com","LISPOST","*").cuerpo().contains("GANADOR"));
        assertTrue(enviar(mail,"intruso@example.com","AUDCAL[\"ASIPLA\",\"1\"]",List.of()).cuerpo().contains("rol requerido"));
        orden(mail,"ensayo@example.com","INSPER","990900137","Ana","Ensayo","Estudiante",
                "3330000","70000137","persona@example.com");
        assertEquals(1,cantidad("auditoria a join postulacion p on p.id=a.postulacion_id " +
                "join correo_control c on c.mensaje_id=a.mensaje_id " +
                "join buzon b on b.protocolo=c.buzon_protocolo where a.accion='INSPOST' and b.protocolo='imap'"));
        assertEquals(1,cantidad("auditoria a join persona p on p.ci=a.persona_ci " +
                "join correo_control c on c.mensaje_id=a.mensaje_id where a.accion='INSPER' and p.ci='990900137'"));
        assertEquals(1,cantidad("expediente"));assertEquals(0,repo.correosPendientes(100).size());
    }
    @Test void noRespondeASusRespuestasNiReenviaLasQueYaEstabanEnCola()throws Exception{
        var mail=new CorreoFalso();
        mail.entrada.add(new MensajeEntrada("eco-error",cfg.mailAddress(),Resultado.error("Prueba").asunto()));
        mail.entrada.add(new MensajeEntrada("eco-exito",cfg.mailAddress(),Resultado.ok("Prueba").asunto()));
        mail.entrada.add(new MensajeEntrada("eco-confirmacion",cfg.mailAddress(),"CONFIRMACIÓN REQUERIDA - INSUSU"));
        mail.entrada.add(new MensajeEntrada("orden-propia",cfg.mailAddress(),"AYUDA[]"));
        repo.registrarEntrada("eco-encolado",cfg.mailAddress(),Resultado.error("Prueba").asunto());
        repo.promoverDiferidos(25);repo.guardarRespuesta("eco-encolado",Resultado.error("No reenviar"));
        ciclo(mail);ciclo(mail);ciclo(mail);
        assertEquals(List.of("orden-propia"),mail.respuestas);
        for(String id:List.of("eco-error","eco-exito","eco-confirmacion","eco-encolado","orden-propia"))assertTrue(repo.correoProcesado(id));
    }
    @Test void respondeConLaCausaDelRechazoDelPdf()throws Exception {
        var mail=new CorreoFalso();mail.entrada.add(new MensajeEntrada("pdf-malo","a@example.com","ADJUNTO_RECHAZADO: Nombre de PDF inválido"));
        ciclo(mail);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("select respuesta_cuerpo from correo_control where mensaje_id='pdf-malo'")){
            assertTrue(r.next());assertEquals("Nombre de PDF inválido",r.getString(1));
        }
    }
    @Test void lisperConFalloTransitorioDeConsultaEnviaUnErrorAlRemitente() throws Exception {
        var consultas = new java.util.concurrent.atomic.AtomicInteger();
        var repositorioConFallo = new RepositorioProyecto() {
            @Override public boolean autorizado(String remitente, String rol) { return false; }
            @Override public boolean hayUsuarios() { return false; }
            @Override public Resultado ejecutar(String remitente, Orden orden) throws java.sql.SQLException {
                assertEquals("LISPER", orden.comando());
                consultas.incrementAndGet();
                throw new java.sql.SQLTransientConnectionException("detalle privado de ensayo", "08006");
            }
        };
        processor = new ProcesadorOrden(new CatalogoComandos(), repositorioConFallo);
        var mail = new CorreoFalso();
        mail.entrada.add(new MensajeEntrada("consulta-interrumpida", "a@example.com", "LISPER[\"*\"]"));
        ciclo(mail);
        ciclo(mail);
        ciclo(mail);
        assertEquals(List.of("consulta-interrumpida"), mail.respuestas);
        var respuesta = mail.detalle.get("consulta-interrumpida");
        assertEquals("ERROR - Orden no procesada", respuesta.asunto());
        assertTrue(respuesta.cuerpo().contains("problema temporal con PostgreSQL"));
        assertFalse(respuesta.cuerpo().contains("detalle privado"));
        assertEquals(1, consultas.get());
        assertTrue(repo.correoProcesado("consulta-interrumpida"));
    }
    @Test void envioFallidoConservaRespuestaYNoDuplicaElResultado()throws Exception{
        var mail=new CorreoFalso();mail.entrada.add(new MensajeEntrada("uno","a@example.com","LISCON[\"*\"]"));
        ciclo(mail);assertEquals(1,cantidad("correo_control where resultado_exito is not null"));assertTrue(mail.marcados.contains("uno"));
        mail.fallaEnvio=true;ciclo(mail);assertFalse(repo.correoProcesado("uno"));assertEquals(1,cantidad("correo_control"));
        sql("update correo_control set siguiente_intento=now()");mail.fallaEnvio=false;ciclo(mail);
        assertTrue(repo.correoProcesado("uno"));assertEquals(1,mail.respuestas.size());assertEquals(1,cantidad("correo_control where resultado_exito is not null"));assertEquals(3,mail.liberaciones);
    }
    @Test void pop3AvisaSinPostgresYRecuperaElHistorialTrasReinicio(
            @org.junit.jupiter.api.io.TempDir Path temporal) throws Exception {
        var repoPop = new RepositorioPostgres(ds, "pop3");
        var mail = new CorreoFalso();
        mail.entrada.add(new MensajeEntrada("historico", "a@example.com", "LISPER[\"*\"]"));
        Path archivo = temporal.resolve("consultas-sin-postgres.tsv");
        var controlador = new ControladorCorreo("pop3", 25, 300, mail, repoPop, processor,
                new AnalizadorOrden(), a -> true);
        controlador.habilitarAvisosConsulta(archivo);
        controlador.ejecutarCiclo();
        mail.entrada.add(new MensajeEntrada("consulta-nueva", "a@example.com", "LISPER[\"*\"]"));
        mail.entrada.add(new MensajeEntrada("alta-diferida", "a@example.com",
                "INSPER[\"10001\",\"Ana\",\"Prueba\",\"Estudiante\",\"123\",\"456\",\"a@example.com\"]"));
        controlador.responderConsultasSinPostgres();
        controlador.responderConsultasSinPostgres();
        assertEquals(List.of("consulta-nueva"), mail.respuestas);
        assertTrue(mail.detalle.get("consulta-nueva").cuerpo().contains("PostgreSQL no está disponible"));
        assertEquals(0, cantidad("persona"));
        assertTrue(Files.isRegularFile(archivo));

        var reiniciado = new ControladorCorreo("pop3", 25, 300, mail, repoPop, processor,
                new AnalizadorOrden(), a -> true);
        reiniciado.habilitarAvisosConsulta(archivo);
        reiniciado.ejecutarCiclo();
        reiniciado.ejecutarCiclo();
        assertEquals(List.of("consulta-nueva", "alta-diferida"), mail.respuestas);
        assertEquals(1, cantidad("persona"));
        assertEquals(1, cantidad("correo_control where mensaje_id='consulta-nueva' and estado='RESPONDIDO' and not exito"));
        assertFalse(Files.exists(archivo));
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
        ciclo(mail);assertEquals(1,cantidad("correo_control"));mail.fallaMarca=false;ciclo(mail);assertTrue(repo.correoProcesado("persistido"));assertEquals(1,mail.respuestas.size());
    }
    @Test void errorAlMarcarDespuesDelEnvioNoProgramaUnReintento() throws Exception {
        var mail = new CorreoFalso();
        mail.entrada.add(new MensajeEntrada("envio-ok", "a@example.com", "AYUDA[]"));
        ciclo(mail);
        mail.fallaMarca = true;
        ciclo(mail);
        ciclo(mail);
        assertTrue(repo.correoProcesado("envio-ok"));
        assertEquals(List.of("envio-ok"), mail.respuestas);
        assertEquals(1, cantidad("correo_control where intentos=0 and ultimo_error is null"));
    }

    @Test void conservaElCodigoDuranteLaCaidaYDaTiempoParaConfirmarAlEntregarlo() throws Exception {
        var mail = new CorreoFalso();
        mail.entrada.add(new MensajeEntrada("reto", "admin@example.com",
                "INSUSU[\"1\",\"Ana\",\"Ensayo\",\"DECANO\",\"70000000\",\"admin@example.com\",\"true\"]"));
        ciclo(mail);
        String pendiente = cuerpoRespuesta("reto");
        var codigo = java.util.regex.Pattern.compile("CONFIRMAR\\[\"[A-Za-z0-9_-]{43}\"\\]").matcher(pendiente);
        assertTrue(codigo.find());
        sql("update correo_control set vence_en=now()-interval '1 minute' where mensaje_id='reto'");
        mail.fallaEnvio = true;
        ciclo(mail);
        assertEquals(pendiente, cuerpoRespuesta("reto"));
        assertEquals(1, cantidad("correo_control where token_hash is not null and mensaje_id='reto' and vence_en<now()"));
        sql("update correo_control set siguiente_intento=now()");
        mail.fallaEnvio = false;
        ciclo(mail);
        assertEquals(pendiente, mail.detalle.get("reto").cuerpo());
        assertTrue(cuerpoRespuesta("reto").contains("CONFIRMAR[\"[OCULTO]\"]"));
        assertFalse(cuerpoRespuesta("reto").matches("(?s).*CONFIRMAR\\[\"[A-Za-z0-9_-]{43}\"\\].*"));
        assertEquals(1, cantidad("correo_control where mensaje_id='reto' and vence_en=actualizado_en+interval '30 minutes'"));
        var confirmacion = enviar(mail, "admin@example.com", codigo.group(), List.of());
        assertEquals("ÉXITO - Orden procesada", confirmacion.asunto(), confirmacion.cuerpo());
        assertEquals(1, cantidad("usuario where correo='admin@example.com'"));
    }

    private String cuerpoRespuesta(String id) throws Exception {
        try (var c = ds.getConnection();
             var q = c.prepareStatement("select respuesta_cuerpo from correo_control where mensaje_id=?")) {
            q.setString(1, id);
            try (var resultado = q.executeQuery()) {
                assertTrue(resultado.next());
                return resultado.getString(1);
            }
        }
    }

    record Respuesta(String asunto,String cuerpo){}
    static class CorreoFalso implements PasarelaCorreo {
        Map<String,Respuesta> detalle=new HashMap<>();int limite=Integer.MAX_VALUE;
        List<MensajeEntrada> entrada=new ArrayList<>();List<String> respuestas=new ArrayList<>();Set<String> marcados=new HashSet<>();int liberaciones;boolean fallaEnvio,fallaLectura,fallaMarca;
        public List<String> identificadores(){return entrada.stream().map(MensajeEntrada::id).toList();}
        public List<MensajeEntrada> noProcesados(ConsultaProcesados consulta)throws Exception{if(fallaLectura)throw new java.io.IOException("Lectura simulada fallida");var conocidos=consulta.conocidos(identificadores());return entrada.stream().filter(m->!conocidos.contains(m.id())).limit(limite).toList();}
        public void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception{if(fallaEnvio)throw new java.io.IOException("Envío simulado fallido");respuestas.add(original.id());detalle.put(original.id(),new Respuesta(asunto,cuerpo));}
        public void marcarProcesado(MensajeEntrada mensaje)throws Exception{if(fallaMarca)throw new java.io.IOException("Marcado simulado fallido");marcados.add(mensaje.id());}
        public void liberarReferencias(){liberaciones++;}
        public void close(){}
    }
}
