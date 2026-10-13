package bo.edu.uagrm.inf513.grupo09sa.comandos;

import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioProyecto;
import bo.edu.uagrm.inf513.grupo09sa.InfraestructuraException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ProcesadorOrden {
    private static final Pattern CORREO=Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ROLES=Set.of("DECANO","VICEDECANO","EVALUADOR","POSTULANTE");
    private static final Set<String> ESTADOS_CONVOCATORIA=Set.of("BORRADOR","ABIERTA","CERRADA","CANCELADA","FINALIZADA");
    private final CatalogoComandos catalogo; private final RepositorioProyecto repo; private final String bootstrapAdminEmail;
    public ProcesadorOrden(CatalogoComandos catalogo,RepositorioProyecto repo){this(catalogo,repo,"");}
    public ProcesadorOrden(CatalogoComandos catalogo,RepositorioProyecto repo,String bootstrapAdminEmail){this.catalogo=catalogo;this.repo=repo;this.bootstrapAdminEmail=bootstrapAdminEmail==null?"":bootstrapAdminEmail.trim();}
    public Resultado procesar(String remitente, Orden orden){return procesar(null,remitente,orden);}
    public Resultado procesar(String mensajeId,String remitente, Orden orden){
        return procesarInterno(mensajeId,remitente,orden,false);
    }
    private Resultado procesarInterno(String mensajeId,String remitente,Orden orden,boolean confirmado){
        try{
            if(orden.comando().equals("CONFIRMAR")){
                if(confirmado||orden.parametros().size()!=1||!orden.parametros().get(0).matches("[A-Za-z0-9_-]{43}"))return Resultado.error("Confirmación inválida.");
                return repo.confirmarOrden(mensajeId,remitente,orden.parametros().get(0),(original,o)->procesarInterno(original,remitente,o,true));
            }
            CatalogoComandos.Definicion def=catalogo.buscar(orden.comando());
            if(def==null)return Resultado.error("El comando " + orden.comando() + " no existe. Use AYUDA[].");
            if(orden.parametros().size()!=def.parametros())return Resultado.error("Cantidad incorrecta de parámetros para " + orden.comando()+". Esperados: "+def.parametros()+"; recibidos: "+orden.parametros().size()+".");
            String validacion=validar(orden);if(validacion!=null)return Resultado.error(validacion);
            boolean bootstrap=orden.comando().equals("INSUSU")&&!repo.hayUsuarios()&&orden.parametros().get(3).equals("DECANO")&&!bootstrapAdminEmail.isBlank()&&remitente.equalsIgnoreCase(bootstrapAdminEmail)&&orden.parametros().get(5).equalsIgnoreCase(remitente);
            if(!bootstrap&&!def.rol().equals("PUBLICO")&&!repo.autorizado(remitente,def.rol()))return Resultado.error("El remitente no tiene el rol requerido: "+def.rol()+".");
            if(!confirmado&&!def.rol().equals("PUBLICO"))return repo.solicitarConfirmacion(mensajeId,remitente,orden);
            if(orden.comando().equals("AYUDA"))return Resultado.ok(catalogo.todos().entrySet().stream().map(e->e.getKey()+" - "+e.getValue().descripcion()+" ("+e.getValue().parametros()+" parámetros)").sorted().collect(Collectors.joining("\n")));
            return mensajeId==null?repo.ejecutar(remitente,orden):repo.ejecutarIdempotente(mensajeId,remitente,orden);
        }catch(Exception ex){
            if(esFalloTemporal(ex))throw new InfraestructuraException("Servicio de datos temporalmente no disponible",ex);
            if(ex instanceof SQLException s)return Resultado.error(mensajeSqlSeguro(s));
            return Resultado.error("No se pudo ejecutar la orden: "+mensajeSeguro(ex));
        }
    }
    private String validar(Orden o){
        try{
            for(String p:o.parametros()){
                if(p==null||p.isBlank())return "Los parámetros no pueden estar vacíos.";
                if(!p.equals(p.trim()))return "Los parámetros no pueden comenzar ni terminar con espacios.";
                if(p.chars().anyMatch(c->Character.isISOControl(c)))return "Los parámetros no pueden contener caracteres de control.";
            }
            switch(o.comando()){
                case "INSUSU"->{max(o,20,100,120,20,30,160,5);soloDigitos(o.parametros().get(0),"registro");if(!ROLES.contains(o.parametros().get(3)))return "Rol inválido. Use exactamente DECANO, VICEDECANO, EVALUADOR o POSTULANTE.";if(!CORREO.matcher(o.parametros().get(5)).matches())return "Correo electrónico inválido.";booleanActivo(o.parametros().get(6));}
                case "INSCON"->{max(o,30,200,10,10,20,9);LocalDate inicio=LocalDate.parse(o.parametros().get(2));LocalDate fin=LocalDate.parse(o.parametros().get(3));if(fin.isBefore(inicio))return "La fecha final no puede ser anterior a la fecha inicial.";if(!ESTADOS_CONVOCATORIA.contains(o.parametros().get(4)))return "Estado de convocatoria inválido. Use mayúsculas.";enteroNoNegativo(o.parametros().get(5));}
                case "INSCAR"->{max(o,160,160,5);booleanActivo(o.parametros().get(2));}
                case "INSPOST"->{max(o,19,19,500);enteroPositivo(o.parametros().get(0));enteroPositivo(o.parametros().get(1));}
                case "ASIEVA"->{max(o,19,19,40);enteroPositivo(o.parametros().get(0));enteroPositivo(o.parametros().get(1));}
                case "EVAREQ"->{max(o,19,200,5,500);enteroPositivo(o.parametros().get(0));booleanActivo(o.parametros().get(2));}
                case "HABPOS","ASIPLA"->{max(o,o.comando().equals("ASIPLA")?new int[]{19,5}:new int[]{19,30,500});enteroPositivo(o.parametros().get(0));if(o.comando().equals("ASIPLA"))booleanActivo(o.parametros().get(1));else if(!Set.of("HABILITADO","INHABILITADO").contains(o.parametros().get(1)))return "Estado inválido. Use exactamente HABILITADO o INHABILITADO.";}
                case "REGMER"->{max(o,19,200,8,500);enteroPositivo(o.parametros().get(0));puntaje(o.parametros().get(2));}
                case "REGEXA"->{max(o,19,8,500);enteroPositivo(o.parametros().get(0));puntaje(o.parametros().get(1));}
                case "GENRAN","REPEST"->enteroPositivo(o.parametros().get(0));
                case "AUDCAL"->{max(o,80,80);enteroPositivo(o.parametros().get(1));}
                case "LISUSU","LISCON","LISCAR","LISPOST"->max(o,100);
                case "AYUDA" -> { }
                default -> { return "Comando no reconocido por el validador."; }
            }
            return null;
        }catch(Exception e){return "Parámetro inválido: "+e.getMessage();}
    }
    private static void enteroPositivo(String v){try{if(Long.parseLong(v)<=0)throw new IllegalArgumentException();}catch(Exception ex){throw new IllegalArgumentException("se esperaba un entero positivo");}}
    private static void enteroNoNegativo(String v){try{if(Integer.parseInt(v)<0)throw new IllegalArgumentException();}catch(Exception ex){throw new IllegalArgumentException("se esperaba un entero no negativo dentro del rango permitido");}}
    private static void puntaje(String v){BigDecimal n=new BigDecimal(v);if(n.scale()>2||n.compareTo(BigDecimal.ZERO)<0||n.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("el puntaje debe estar entre 0 y 100 y tener máximo 2 decimales");}
    private static void soloDigitos(String v,String nombre){if(!v.matches("[0-9]+"))throw new IllegalArgumentException(nombre+" debe contener solo dígitos");}
    private static void max(Orden o,int... limites){for(int i=0;i<limites.length;i++)if(o.parametros().get(i).length()>limites[i])throw new IllegalArgumentException("parámetro "+(i+1)+" supera "+limites[i]+" caracteres");}
    private static void booleanActivo(String v){if(!v.equalsIgnoreCase("true")&&!v.equalsIgnoreCase("false"))throw new IllegalArgumentException("se esperaba true o false");}
    private static boolean esFalloTemporal(Throwable e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof SQLException s){String estado=s.getSQLState();if(s instanceof java.sql.SQLTransientException||(estado!=null&&(estado.startsWith("08")||Set.of("40001","40P01","55P03","57014","57P01","57P02","57P03","53300").contains(estado))))return true;}return false;}
    private static String mensajeSqlSeguro(SQLException e){String estado=e.getSQLState();if("23505".equals(estado))return "El registro ya existe; no se realizaron cambios.";if("23503".equals(estado))return "No existe el registro relacionado solicitado; no se realizaron cambios.";if("23514".equals(estado)||"22003".equals(estado)||"22P02".equals(estado)||"22007".equals(estado))return "Uno de los datos está fuera del formato o rango permitido; no se realizaron cambios.";return "No se pudo ejecutar la orden de forma segura. Código de base de datos: "+(estado==null?"desconocido":estado)+".";}
    private static String mensajeSeguro(Throwable e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m.replaceAll("(?i)(password|contraseña)\\s*[=:]\\s*\\S+","$1=[OCULTO]");}
}
