package bo.edu.uagrm.inf513.grupo09sa;

import bo.edu.uagrm.inf513.grupo09sa.comandos.*;
import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import bo.edu.uagrm.inf513.grupo09sa.email.*;
import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioPostgres;
import bo.edu.uagrm.inf513.grupo09sa.presentacion.ControladorCorreo;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.postgresql.ds.PGSimpleDataSource;

public class EnsayoManual {
    private static final String DECANO = "decano@example.com";
    private static final String PUBLICO = "visitante@example.com";

    private static final class Bandeja implements PasarelaCorreo {
        private MensajeEntrada entrada;
        private String asunto;
        private String cuerpo;
        public List<String> identificadores() { return List.of(); }
        public List<MensajeEntrada> noProcesados(ConsultaProcesados consulta) {
            return entrada == null ? List.of() : List.of(entrada);
        }
        public void responder(MensajeEntrada original, String asunto, String cuerpo) {
            this.asunto = asunto;
            this.cuerpo = cuerpo;
            System.out.println("Respuesta a " + original.remitente() + "\n" + asunto + "\n" + cuerpo);
        }
        public void marcarProcesado(MensajeEntrada mensaje) { entrada = null; }
        public void close() {}
    }

    private final AppConfig config = new AppConfig("imap", "localhost", 993, true,
            "localhost", 465, true, true, "smtp", "https://localhost/", "ensayo",
            "ensayo@example.com", "local", "jdbc:postgresql://127.0.0.1:55439/ensayo",
            "ensayo", "ensayo-local", DECANO, 1, 1, 1, 25, 300, "INBOX");
    private final Bandeja bandeja = new Bandeja();
    private final AnalizadorOrden analizador = new AnalizadorOrden();
    private final RepositorioPostgres repositorio;
    private final ControladorCorreo controlador;

    private EnsayoManual() throws Exception {
        var datos = new PGSimpleDataSource();
        datos.setURL(config.dbUrl());
        datos.setUser(config.dbUser());
        datos.setPassword(config.dbPassword());
        String esquema = "manual_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexion = datos.getConnection(); var sql = conexion.createStatement()) {
            sql.execute("create schema " + esquema);
        }
        datos.setCurrentSchema(esquema);
        try (var conexion = datos.getConnection(); var sql = conexion.createStatement()) {
            sql.execute(Files.readString(Path.of("database/schema.sql")));
        }
        repositorio = new RepositorioPostgres(datos, config.inboundProtocol());
        var procesador = new ProcesadorOrden(new CatalogoComandos(), repositorio, DECANO);
        controlador = new ControladorCorreo(config.inboundProtocol(), config.maxMessagesPerCycle(),
                config.retryMaxSeconds(), bandeja, repositorio, procesador, analizador, AntivirusPdf.desdeEntorno());
        ciclo();
    }

    private void ciclo() throws Exception {
        controlador.ejecutarCiclo();
    }

    private String enviar(String remitente, String asunto) throws Exception {
        System.out.println("\nDe " + remitente + "\nAsunto " + asunto);
        bandeja.asunto = null;
        bandeja.entrada = new MensajeEntrada("manual-" + UUID.randomUUID(), remitente, asunto);
        ciclo();
        ciclo();
        if (bandeja.asunto == null) throw new IllegalStateException("El ciclo no entregó respuesta");
        return bandeja.asunto;
    }

    private void demostrar() throws Exception {
        System.out.println("\n1. Consulta pública de convocatorias");
        esperar(enviar(PUBLICO, "LISCON[\"*\"]"), "ÉXITO");
        System.out.println("\n2. Rechazo de un asunto con sintaxis incompleta");
        esperar(enviar(PUBLICO, "AYUDA["), "ERROR");
        System.out.println("\n3. Alta del primer DECANO, con confirmación desde la misma cuenta");
        esperar(enviar(DECANO,
                "INSUSU[\"990900001\",\"Ana\",\"Ensayo\",\"DECANO\",\"70000001\",\"decano@example.com\",\"true\"]"),
                "CONFIRMACIÓN REQUERIDA");
        var codigo = Pattern.compile("CONFIRMAR\\[\"[A-Za-z0-9_-]{43}\"\\]").matcher(bandeja.cuerpo);
        if (!codigo.find()) throw new IllegalStateException("Falta la orden de confirmación");
        esperar(enviar(DECANO, codigo.group()), "ÉXITO");
    }

    private static void esperar(String respuesta, String inicio) {
        if (!respuesta.startsWith(inicio)) throw new IllegalStateException("Se esperaba " + inicio);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("INF513 Grupo 09 SA | Prueba manual en Fedora");
        System.out.println("Java y PostgreSQL local. Bandeja de correo simulada.");
        var ensayo = new EnsayoManual();
        ensayo.demostrar();
        System.out.println("\nAhora probá vos. Pegá un asunto y presioná Enter.");
        System.out.println("AYUDA[]        Ver órdenes disponibles");
        System.out.println("LISCON[\"*\"]    Consultar convocatorias");
        System.out.println("LISUSU[\"*\"]    Pedir listado privado como DECANO");
        System.out.println("/publico       Cambiar a visitante@example.com");
        System.out.println("/decano        Cambiar a decano@example.com");
        System.out.println("/evaluador     Cambiar a evaluador@example.com");
        System.out.println("/postulante    Cambiar a postulante@example.com");
        System.out.println("/salir         Terminar");
        String remitente = DECANO;
        try (var teclado = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n" + remitente + " > ");
                System.out.flush();
                if (!teclado.hasNextLine()) break;
                String asunto = teclado.nextLine().strip();
                switch (asunto) {
                    case "" -> {}
                    case "/salir" -> { return; }
                    case "/publico" -> remitente = PUBLICO;
                    case "/decano" -> remitente = DECANO;
                    case "/evaluador" -> remitente = "evaluador@example.com";
                    case "/postulante" -> remitente = "postulante@example.com";
                    default -> ensayo.enviar(remitente, asunto);
                }
            }
        }
    }
}
