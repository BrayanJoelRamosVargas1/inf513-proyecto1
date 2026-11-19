package bo.edu.uagrm.inf513.grupo09sa.presentacion;

import bo.edu.uagrm.inf513.grupo09sa.BitacoraArchivos;
import bo.edu.uagrm.inf513.grupo09sa.comandos.AnalizadorOrden;
import bo.edu.uagrm.inf513.grupo09sa.comandos.OrdenInvalidaException;
import bo.edu.uagrm.inf513.grupo09sa.comandos.Resultado;
import bo.edu.uagrm.inf513.grupo09sa.email.PasarelaCorreo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/** Permite avisar por SMTP a nuevas consultas POP3 mientras la cola PostgreSQL está caída. */
final class AvisosConsulta {
    private static final int MAX_AVISOS = 1000;
    private static final int MAX_OMITIDOS = 10_000;
    private final Path archivo;
    private final LinkedHashMap<String, String> enviados = new LinkedHashMap<>();
    private final Set<String> omitidos = new HashSet<>();
    private Set<String> bandejaObservada;

    AvisosConsulta(Path archivo) throws IOException {
        this.archivo = archivo.toAbsolutePath();
        if (!Files.exists(this.archivo)) return;
        for (String linea : Files.readAllLines(this.archivo)) {
            String[] partes = linea.split("\\t", -1);
            if (partes.length != 2 || !ControladorCorreo.idSeguro(partes[0])
                    || !ControladorCorreo.correoSeguro(partes[1])) {
                throw new IOException("Registro local de avisos de consulta inválido");
            }
            enviados.put(partes[0], partes[1]);
        }
    }

    void observar(Set<String> ids) {
        bandejaObservada = ids;
        omitidos.clear();
    }

    void reconciliar(ColaCorreo cola) throws Exception {
        if (enviados.isEmpty()) return;
        for (var entrada : enviados.entrySet()) {
            cola.registrarCorreoProcesado(entrada.getKey(), entrada.getValue(), "LISPER", false);
        }
        Files.deleteIfExists(archivo);
        enviados.clear();
    }

    void responder(PasarelaCorreo correo, AnalizadorOrden analizador) throws Exception {
        if (bandejaObservada == null || enviados.size() >= MAX_AVISOS || omitidos.size() >= MAX_OMITIDOS)
            return;
        try {
            for (var mensaje : correo.noProcesados(ids -> {
                var conocidos = new HashSet<String>();
                for (String id : ids) {
                    if (bandejaObservada.contains(id) || enviados.containsKey(id) || omitidos.contains(id))
                        conocidos.add(id);
                }
                return conocidos;
            })) {
                if (enviados.size() >= MAX_AVISOS || omitidos.size() >= MAX_OMITIDOS) break;
                if (!ControladorCorreo.idSeguro(mensaje.id()) || enviados.containsKey(mensaje.id())) continue;
                if (!ControladorCorreo.correoSeguro(mensaje.remitente()) || !consultaPersona(analizador, mensaje.asunto())) {
                    omitidos.add(mensaje.id());
                    continue;
                }
                Resultado aviso = Resultado.error("No se pudo completar la consulta de personas porque PostgreSQL "
                        + "no está disponible. Volvé a enviar LISPER cuando se restablezca el servicio.");
                correo.responder(mensaje, aviso.asunto(), aviso.cuerpo());
                enviados.put(mensaje.id(), mensaje.remitente());
                guardar();
                BitacoraArchivos.sistema().acceso("RESPONDIDO_SIN_POSTGRES", mensaje.id(), mensaje.remitente(), "LISPER", false);
            }
        } finally {
            correo.liberarReferencias();
        }
    }

    private static boolean consultaPersona(AnalizadorOrden analizador, String asunto) {
        try {
            var orden = analizador.analizar(asunto);
            return orden.comando().equals("LISPER") && orden.parametros().size() == 1;
        } catch (OrdenInvalidaException invalida) {
            return false;
        }
    }

    private void guardar() throws IOException {
        var contenido = new StringBuilder();
        enviados.forEach((id, remitente) -> contenido.append(id).append('\t').append(remitente).append('\n'));
        Files.createDirectories(archivo.getParent());
        Path temporal = Files.createTempFile(archivo.getParent(), "consultas-", ".tmp");
        try {
            Files.writeString(temporal, contenido);
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
