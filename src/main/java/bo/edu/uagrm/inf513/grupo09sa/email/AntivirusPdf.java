package bo.edu.uagrm.inf513.grupo09sa.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Local antivirus only: documents are not uploaded to public scanning services. */
public final class AntivirusPdf implements InspectorPdf {
    private final List<String> comando;
    private final int segundos;
    private final Path temporal;

    AntivirusPdf(List<String> comando,int segundos,Path temporal){
        this.comando=List.copyOf(comando);this.segundos=segundos;this.temporal=temporal;
    }

    public static InspectorPdf desdeEntorno(){
        try {
            boolean windows=System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");
            String motor=System.getenv().getOrDefault("PDF_ANTIVIRUS",windows?"defender":"clamav");
            Path exe;
            String configurado=System.getenv("PDF_ANTIVIRUS_EXECUTABLE");
            if(configurado!=null&&!configurado.isBlank())exe=Path.of(configurado);
            else if(motor.equals("defender")&&windows)exe=defender();
            else if(motor.equals("clamav"))exe=Path.of("/usr/bin/clamscan");
            else throw new IOException("Motor de antivirus no configurado");
            if(!exe.isAbsolute()||!Files.isRegularFile(exe))throw new IOException("Antivirus local no disponible");
            String nombre=exe.getFileName().toString();
            List<String> argumentos;
            if(motor.equals("defender")&&nombre.equalsIgnoreCase("MpCmdRun.exe"))
                argumentos=List.of(exe.toString(),"-Scan","-ScanType","3","-DisableRemediation","-File");
            else if(motor.equals("clamav")&&(nombre.equals("clamscan")||nombre.equals("clamscan.exe")))
                argumentos=List.of(exe.toString(),"--no-summary","--infected","--max-filesize=3M","--max-scansize=16M","--max-recursion=8","--alert-exceeds-max=yes","--");
            else throw new IOException("Ejecutable de antivirus no reconocido");
            return new AntivirusPdf(argumentos,15,Path.of(System.getProperty("java.io.tmpdir")));
        } catch(IOException|RuntimeException noDisponible){
            // Public commands keep working; an unavailable scanner never approves a PDF.
            return archivo->{throw new IOException("Antivirus local no disponible; el PDF no fue almacenado");};
        }
    }

    private static Path defender()throws IOException{
        Path plataforma=Path.of(System.getenv().getOrDefault("ProgramData","C:\\ProgramData"),"Microsoft","Windows Defender","Platform");
        if(Files.isDirectory(plataforma))try(var versiones=Files.list(plataforma)){
            var elegido=versiones.filter(Files::isDirectory).sorted(Comparator.reverseOrder())
                .map(v->v.resolve("MpCmdRun.exe")).filter(Files::isRegularFile).findFirst();
            if(elegido.isPresent())return elegido.get();
        }
        return Path.of(System.getenv().getOrDefault("ProgramFiles","C:\\Program Files"),"Windows Defender","MpCmdRun.exe");
    }

    @Override public boolean aprobado(AdjuntoPdf archivo)throws IOException,InterruptedException{
        Path directorio=Files.getFileStore(temporal).supportsFileAttributeView("posix")
            ?Files.createTempDirectory(temporal,"inf513-pdf-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            :Files.createTempDirectory(temporal,"inf513-pdf-");
        Path pdf=directorio.resolve("documento.pdf");Process proceso=null;
        try {
            Files.write(pdf,archivo.contenido());
            var argumentos=new ArrayList<>(comando);argumentos.add(pdf.toString());
            ProcessBuilder builder=new ProcessBuilder(argumentos).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            // Do not hand mail/database credentials (or unrelated application secrets) to a child.
            var heredado=new java.util.HashMap<>(builder.environment());builder.environment().clear();
            var permitidas=java.util.Set.of("systemroot","windir","programdata","programfiles","programfiles(x86)","temp","tmp","lang","lc_all","tz","userprofile","localappdata","appdata","systemdrive","username","userdomain");
            for(var variable:heredado.entrySet())
                if(permitidas.contains(variable.getKey().toLowerCase(Locale.ROOT)))builder.environment().put(variable.getKey(),variable.getValue());
            proceso=builder.start();
            if(!proceso.waitFor(segundos,TimeUnit.SECONDS))throw new IOException("El antivirus superó el tiempo de análisis");
            // Nonzero includes detections and scanner errors. Neither is a clean result.
            return proceso.exitValue()==0&&Files.isRegularFile(pdf);
        } finally {
            if(proceso!=null&&proceso.isAlive()){
                proceso.destroyForcibly();
                if(!proceso.waitFor(2,TimeUnit.SECONDS))throw new IOException("No se pudo detener el análisis del PDF");
            }
            Files.deleteIfExists(pdf);Files.deleteIfExists(directorio);
        }
    }
}
