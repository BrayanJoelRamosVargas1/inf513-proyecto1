package bo.edu.uagrm.inf513.grupo09sa.comandos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnalizadorOrden {
    private static final Pattern CABECERA=Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\[(.*)]\\s*$",Pattern.DOTALL);
    public Orden analizar(String asunto) throws OrdenInvalidaException {
        if(asunto==null||asunto.isBlank()) throw new OrdenInvalidaException("El asunto está vacío");
        Matcher m=CABECERA.matcher(asunto); if(!m.matches()) throw new OrdenInvalidaException("Formato esperado: COMANDO[\"parametro\"]");
        return new Orden(m.group(1).toUpperCase(Locale.ROOT), parametros(m.group(2)));
    }
    private List<String> parametros(String texto) throws OrdenInvalidaException {
        List<String> out=new ArrayList<>(); int i=0; while(i<texto.length()){
            while(i<texto.length()&&Character.isWhitespace(texto.charAt(i)))i++;
            if(i==texto.length())break; if(texto.charAt(i)!='\"')throw new OrdenInvalidaException("Cada parámetro debe estar entre comillas dobles"); i++;
            StringBuilder b=new StringBuilder(); boolean cerrado=false;
            while(i<texto.length()){
                char c=texto.charAt(i++); if(c=='\\'&&i<texto.length()){char n=texto.charAt(i++); if(n=='\"'||n=='\\')b.append(n); else{b.append(c).append(n);}}
                else if(c=='\"'){cerrado=true;break;} else b.append(c);
            }
            if(!cerrado)throw new OrdenInvalidaException("Parámetro sin comillas de cierre"); out.add(b.toString());
            while(i<texto.length()&&Character.isWhitespace(texto.charAt(i)))i++;
            if(i<texto.length()){if(texto.charAt(i)!=',')throw new OrdenInvalidaException("Los parámetros deben separarse con comas"); i++;if(i>=texto.length())throw new OrdenInvalidaException("No se permite una coma final sin parámetro");}
        }
        return List.copyOf(out);
    }
}
