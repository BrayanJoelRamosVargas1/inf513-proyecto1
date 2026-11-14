package bo.edu.uagrm.inf513.grupo09sa.email;
public record MensajeEntrada(String id,String remitente,String asunto,java.util.List<AdjuntoPdf> adjuntos) {
    public MensajeEntrada(String id,String remitente,String asunto){this(id,remitente,asunto,java.util.List.of());}
    public MensajeEntrada { adjuntos=java.util.List.copyOf(adjuntos);if(adjuntos.size()>1)throw new IllegalArgumentException("Se admite un PDF por orden"); }
}
