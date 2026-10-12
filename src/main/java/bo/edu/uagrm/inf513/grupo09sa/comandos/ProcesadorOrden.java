package bo.edu.uagrm.inf513.grupo09sa.comandos;

import bo.edu.uagrm.inf513.grupo09sa.persistencia.RepositorioProyecto;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ProcesadorOrden {
    private static final Pattern CORREO=Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ROLES=Set.of("DECANO","VICEDECANO","EVALUADOR","POSTULANTE");
    private final CatalogoComandos catalogo; private final RepositorioProyecto repo;
    public ProcesadorOrden(CatalogoComandos catalogo,RepositorioProyecto repo){this.catalogo=catalogo;this.repo=repo;}
    public Resultado procesar(String remitente, Orden orden){
        try{
            CatalogoComandos.Definicion def=catalogo.buscar(orden.comando());
            if(def==null)return Resultado.error("El comando " + orden.comando() + " no existe. Use AYUDA[].");
            if(orden.parametros().size()!=def.parametros())return Resultado.error("Cantidad incorrecta de parámetros para " + orden.comando()+". Esperados: "+def.parametros()+"; recibidos: "+orden.parametros().size()+".");
            String validacion=validar(orden);if(validacion!=null)return Resultado.error(validacion);
            boolean bootstrap=orden.comando().equals("INSUSU")&&!repo.hayUsuarios()&&orden.parametros().get(3).equalsIgnoreCase("DECANO");
            if(!bootstrap&&!def.rol().equals("PUBLICO")&&!repo.autorizado(remitente,def.rol()))return Resultado.error("El remitente no tiene el rol requerido: "+def.rol()+".");
            if(orden.comando().equals("AYUDA"))return Resultado.ok(catalogo.todos().entrySet().stream().map(e->e.getKey()+" - "+e.getValue().descripcion()+" ("+e.getValue().parametros()+" parámetros)").sorted().collect(Collectors.joining("\n")));
            return repo.ejecutar(remitente,orden);
        }catch(Exception ex){return Resultado.error("No se pudo ejecutar la orden: "+ex.getMessage());}
    }
    private String validar(Orden o){
        try{
            for(String p:o.parametros())if(p==null||p.isBlank())return "Los parámetros no pueden estar vacíos.";
            switch(o.comando()){
                case "INSUSU"->{if(!ROLES.contains(o.parametros().get(3).toUpperCase(Locale.ROOT)))return "Rol inválido. Use DECANO, VICEDECANO, EVALUADOR o POSTULANTE.";if(!CORREO.matcher(o.parametros().get(5)).matches())return "Correo electrónico inválido.";booleanActivo(o.parametros().get(6));}
                case "INSCON"->{LocalDate inicio=LocalDate.parse(o.parametros().get(2));LocalDate fin=LocalDate.parse(o.parametros().get(3));if(fin.isBefore(inicio))return "La fecha final no puede ser anterior a la fecha inicial.";enteroNoNegativo(o.parametros().get(5));}
                case "INSCAR"->booleanActivo(o.parametros().get(2));
                case "INSPOST","ASIEVA"->{enteroPositivo(o.parametros().get(0));enteroPositivo(o.parametros().get(1));}
                case "EVAREQ"->{enteroPositivo(o.parametros().get(0));booleanActivo(o.parametros().get(2));}
                case "HABPOS","ASIPLA"->{enteroPositivo(o.parametros().get(0));if(o.comando().equals("ASIPLA"))booleanActivo(o.parametros().get(1));else if(!Set.of("HABILITADO","INHABILITADO").contains(o.parametros().get(1).toUpperCase(Locale.ROOT)))return "Estado inválido. Use HABILITADO o INHABILITADO.";}
                case "REGMER"->{enteroPositivo(o.parametros().get(0));puntaje(o.parametros().get(2));}
                case "REGEXA"->{enteroPositivo(o.parametros().get(0));puntaje(o.parametros().get(1));}
                case "GENRAN","REPEST"->enteroPositivo(o.parametros().get(0));
                case "AUDCAL"->enteroPositivo(o.parametros().get(1));
            }
            return null;
        }catch(Exception e){return "Parámetro inválido: "+e.getMessage();}
    }
    private static void enteroPositivo(String v){if(Long.parseLong(v)<=0)throw new IllegalArgumentException("se esperaba un entero positivo");}
    private static void enteroNoNegativo(String v){if(Integer.parseInt(v)<0)throw new IllegalArgumentException("se esperaba un entero no negativo");}
    private static void puntaje(String v){double n=Double.parseDouble(v);if(n<0||n>100)throw new IllegalArgumentException("el puntaje debe estar entre 0 y 100");}
    private static void booleanActivo(String v){if(!v.equalsIgnoreCase("true")&&!v.equalsIgnoreCase("false"))throw new IllegalArgumentException("se esperaba true o false");}
}
