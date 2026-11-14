package bo.edu.uagrm.inf513.grupo09sa.comandos;
public record Resultado(boolean exito, String asunto, String cuerpo) {
    public static Resultado ok(String cuerpo){return new Resultado(true,"ÉXITO - Orden procesada",cuerpo);}
    public static Resultado error(String cuerpo){return new Resultado(false,"ERROR - Orden no procesada",cuerpo);}
}
