package bo.edu.uagrm.inf513.grupo09sa.email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** A bounded PDF received by mail. File names are metadata, never filesystem paths. */
public record AdjuntoPdf(String nombre, byte[] contenido) {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public AdjuntoPdf {
        if (nombre == null || !nombre.matches("(?i)[\\p{L}\\p{N} _().-]{1,175}\\.pdf")
                || nombre.contains("..")) throw new IllegalArgumentException("Nombre de PDF inválido");
        if (contenido == null || contenido.length < 5 || contenido.length > MAX_BYTES
                || !new String(contenido, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IllegalArgumentException("Adjunte un PDF con cabecera válida y máximo 2 MiB");
        contenido = contenido.clone();
    }
    @Override public byte[] contenido() { return contenido.clone(); }
    public String sha256() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
