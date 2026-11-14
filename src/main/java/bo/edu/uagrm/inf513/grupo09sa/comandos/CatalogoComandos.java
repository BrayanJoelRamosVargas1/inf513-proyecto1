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
        registrar("INSPOST",3,"POSTULANTE","Registrar postulación"); registrar("LISPOST",1,"POSTULANTE","Listar postulaciones");
        registrar("EVAREQ",4,"EVALUADOR","Evaluar requisito"); registrar("HABPOS",3,"DECANO","Habilitar o inhabilitar postulante");
        registrar("REGMER",4,"EVALUADOR","Registrar mérito"); registrar("REGEXA",3,"EVALUADOR","Registrar examen");
        registrar("ASIEVA",3,"DECANO","Asignar evaluador"); registrar("GENRAN",1,"DECANO","Generar ranking");
        registrar("ASIPLA",2,"DECANO","Asignar plaza"); registrar("AUDCAL",2,"DECANO","Consultar auditoría");
        registrar("REPEST",1,"DECANO","Generar estadísticas"); registrar("AYUDA",0,"PUBLICO","Mostrar comandos disponibles");
        registrar("MODUSU",8,"DECANO","Modificar usuario: id,nombres,apellidos,rol,telefono,correo,activo,motivo");
        registrar("PERFIL",3,"DECANO","Perfil académico: usuario_id,facultad,carrera");
        registrar("MODCON",9,"DECANO","Modificar convocatoria: id,titulo,inicio,fin,estado,plazas,gestion,numero,motivo");
        registrar("OFERTA",6,"DECANO","Configurar categoría: convocatoria_id,cargo_id,plazas,facultades,carreras,multiple");
        registrar("LISOFE",1,"PUBLICO","Ofertas de una convocatoria");
        registrar("REQUISITO",6,"DECANO","Requisito: oferta_id,codigo,descripcion,obligatorio,documental,activo");
        registrar("LISREQ",1,"PUBLICO","Requisitos de una oferta");
        registrar("AREA",5,"DECANO","Área: oferta_id,nombre,porcentaje,activo,motivo");
        registrar("LISAREA",1,"PUBLICO","Áreas de una oferta");
        registrar("ASIAREA",3,"DECANO","Asignar área: postulacion_id,evaluador_id,area_id");
        registrar("NOTAREA",4,"EVALUADOR","Nota de área: postulacion_id,area_id,nota_100,motivo");
        registrar("MER40",4,"EVALUADOR","Mérito: postulacion_id,criterio,puntos_40,motivo");
        registrar("DOCPOST",2,"POSTULANTE","Cargar PDF adjunto: postulacion_id,codigo_requisito");
        registrar("LISDOC",1,"LECTOR_EXPEDIENTE","Documentos de una postulación propia o asignada");
        registrar("VERDOC",1,"LECTOR_EXPEDIENTE","Recuperar PDF por id de documento");
        registrar("RESEMP",3,"DECANO","Resolver empate: postulacion_id,autorizar,motivo");
        registrar("REPFIN",1,"DECANO","Resultados consolidados y vacantes por categoría");
        registrar("REPDOC",1,"DECANO","Reporte documental de una convocatoria");
        registrar("REPAREA",1,"DECANO","Calificaciones y ponderaciones de una convocatoria");
    }
    private void registrar(String c,int p,String r,String d){comandos.put(c,new Definicion(p,r,d));}
    public Definicion buscar(String c){return comandos.get(c);}
    public Map<String,Definicion> todos(){return Map.copyOf(comandos);}
}
