package bo.edu.uagrm.inf513.grupo09sa.comandos;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogoComandos {
    public record Definicion(int parametros, String rol, String descripcion) {}
    private final Map<String,Definicion> comandos=new LinkedHashMap<>();
    public CatalogoComandos(){
        registrar("INSPER",7,"PUBLICO","Ejemplo de la consigna: insertar persona"); registrar("LISPER",1,"PUBLICO","Ejemplo de la consigna: listar personas");
        registrar("INSUSU",7,"DECANO","Crear usuario"); registrar("LISUSU",1,"DECANO","Listar usuarios");
        registrar("INSCON",6,"DECANO","Crear convocatoria"); registrar("LISCON",1,"PUBLICO","Listar convocatorias");
        registrar("INSCAR",3,"DECANO","Crear cargo de auxiliatura"); registrar("LISCAR",1,"PUBLICO","Listar cargos de auxiliatura");
        registrar("ASIOFE",3,"DECANO","Configurar plazas por convocatoria y cargo (CU2)");
        registrar("CFGARE",4,"DECANO","Configurar área y porcentaje del examen (CU2/CU5)");
        registrar("LISCFG",1,"DECANO","Consultar ofertas y ponderaciones (CU2)");
        registrar("INSPOST",3,"POSTULANTE","Registrar postulación"); registrar("LISPOST",1,"POSTULANTE","Listar postulaciones");
        registrar("EVAREQ",4,"EVALUADOR","Evaluar requisito"); registrar("HABPOS",3,"DECANO","Habilitar o inhabilitar postulante");
        registrar("REGMER",4,"EVALUADOR","Registrar mérito"); registrar("REGEXA",3,"EVALUADOR","Registrar examen");
        registrar("REGAREA",4,"EVALUADOR","Registrar nota de un área del examen (CU5)");
        registrar("ASIEVA",3,"DECANO","Asignar evaluador"); registrar("GENRAN",1,"DECANO","Generar ranking");
        registrar("ASIPLA",2,"DECANO","Asignar plaza"); registrar("AUDCAL",2,"DECANO","Consultar auditoría");
        registrar("DESEMP",4,"DECANO","Resolver empate: convocatoria, cargo, IDs ordenados, justificación (CU6)");
        registrar("REPEST",1,"DECANO","Generar estadísticas"); registrar("AYUDA",0,"PUBLICO","Mostrar comandos disponibles");
    }
    private void registrar(String c,int p,String r,String d){comandos.put(c,new Definicion(p,r,d));}
    public Definicion buscar(String c){return comandos.get(c);}
    public Map<String,Definicion> todos(){return Map.copyOf(comandos);}
}
