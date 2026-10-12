package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

/** Cliente mínimo para la instalación oficial de Roundcube de Tecnoweb. */
final class ClienteRoundcube implements AutoCloseable {
    private static final Pattern TOKEN_LOGIN = Pattern.compile("name=\\\"_token\\\" value=\\\"([^\\\"]+)\\\"");
    private static final Pattern TOKEN_SESION = Pattern.compile("\\\"request_token\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern ID_COMPOSICION = Pattern.compile("name=\\\"_id\\\" value=\\\"([^\\\"]+)\\\"");
    private static final Pattern TOKEN_FORM = Pattern.compile("name=\\\"_token\\\" value=\\\"([^\\\"]+)\\\"");
    private static final Pattern IDENTIDAD = Pattern.compile("<option value=\\\"([^\\\"]+)\\\" selected");

    private final AppConfig cfg;
    private final BasicCookieStore cookies=new BasicCookieStore();
    private final CloseableHttpClient http;
    private final URI base;
    private String tokenSesion;

    ClienteRoundcube(AppConfig cfg) {
        this.cfg = cfg;
        this.base = URI.create(cfg.roundcubeUrl().endsWith("/") ? cfg.roundcubeUrl() : cfg.roundcubeUrl() + "/");
        this.http=HttpClients.custom().setDefaultCookieStore(cookies).build();
    }

    synchronized void enviar(String destinatario, String asunto, String cuerpo) throws Exception {
        asegurarSesion();
        RespuestaHttp compose = request("GET","?_task=mail&_action=compose&_token=" + enc(tokenSesion),null,Map.of());
        if (compose.codigo() != 200 || compose.cuerpo().contains("id=\"login-form\"")) {
            tokenSesion = null;
            asegurarSesion();
            compose = request("GET","?_task=mail&_action=compose&_token=" + enc(tokenSesion),null,Map.of());
        }
        String html = compose.cuerpo();
        String id = extraer(ID_COMPOSICION, html, "identificador de composición");
        String token = extraer(TOKEN_FORM, html, "token de composición");
        String identidad = extraer(IDENTIDAD, html, "identidad del remitente");

        Map<String,String> f = new LinkedHashMap<>();
        f.put("_task","mail"); f.put("_action","send"); f.put("_id",id); f.put("_token",token);
        f.put("_draft",""); f.put("_draft_saveid",""); f.put("_from",identidad);
        f.put("_to",destinatario); f.put("_cc",""); f.put("_bcc","");
        f.put("_replyto",cfg.mailAddress()); f.put("_followupto","");
        f.put("_subject",asunto); f.put("_message",cuerpo); f.put("_is_html","0");
        f.put("_framed","1"); f.put("_priority","0"); f.put("_store_target","Sent");
        f.put("_attachments",""); f.put("_dsn",""); f.put("_mdn","");
        RespuestaHttp res=request("POST","?_task=mail",form(f),Map.of(
                "Content-Type","application/x-www-form-urlencoded",
                "X-Roundcube-Request",token,"X-Requested-With","XMLHttpRequest"));
        if (res.codigo()!=200 || !res.cuerpo().contains("remove_compose_data") || res.cuerpo().toLowerCase().contains("messageerror")) {
            throw new IllegalStateException("Roundcube no confirmó el envío (HTTP " + res.codigo() + ").");
        }
    }

    private void asegurarSesion() throws Exception {
        if (tokenSesion != null) return;
        RespuestaHttp inicio = request("GET","",null,Map.of());
        String token = extraer(TOKEN_LOGIN,inicio.cuerpo(),"token de acceso");
        Map<String,String> f=new LinkedHashMap<>();
        f.put("_token",token);f.put("_task","login");f.put("_action","login");
        f.put("_timezone","America/La_Paz");f.put("_url","");f.put("_user",cfg.mailUser());f.put("_pass",cfg.mailPassword());
        RespuestaHttp res=request("POST","?_task=login",form(f),Map.of("Content-Type","application/x-www-form-urlencoded"));
        if(res.codigo()!=200 || res.cuerpo().contains("id=\"login-form\""))throw new IllegalStateException("Roundcube rechazó el acceso (HTTP "+res.codigo()+").");
        tokenSesion=extraer(TOKEN_SESION,res.cuerpo(),"token de sesión");
    }

    private RespuestaHttp request(String metodo,String ruta,String cuerpo,Map<String,String> headers)throws Exception{
        URI uri=base.resolve(ruta);
        ClassicHttpRequest req;
        if("POST".equals(metodo)){
            HttpPost post=new HttpPost(uri);
            java.util.List<BasicNameValuePair> pares=new java.util.ArrayList<>();
            if(cuerpo!=null)for(String parte:cuerpo.split("&")){int i=parte.indexOf('=');String k=java.net.URLDecoder.decode(i<0?parte:parte.substring(0,i),StandardCharsets.UTF_8);String v=java.net.URLDecoder.decode(i<0?"":parte.substring(i+1),StandardCharsets.UTF_8);pares.add(new BasicNameValuePair(k,v));}
            post.setEntity(new UrlEncodedFormEntity(pares,StandardCharsets.UTF_8));req=post;
        }else req=new HttpGet(uri);
        req.setHeader("User-Agent","Mozilla/5.0 INF513-Grupo09SA/1.0");
        req.setHeader("Accept","text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        headers.forEach(req::setHeader);
        try(CloseableHttpResponse res=http.execute(req)){
            return new RespuestaHttp(res.getCode(),res.getEntity()==null?"":EntityUtils.toString(res.getEntity(),StandardCharsets.UTF_8));
        }
    }
    private static String extraer(Pattern p,String s,String nombre){Matcher m=p.matcher(s);if(!m.find())throw new IllegalStateException("No se encontró "+nombre+" en Roundcube.");return m.group(1);}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("*","%2A");}
    private static String form(Map<String,String> values){StringBuilder b=new StringBuilder();values.forEach((k,v)->{if(!b.isEmpty())b.append('&');b.append(enc(k)).append('=').append(enc(v));});return b.toString();}
    @Override public void close() throws Exception { http.close(); }
    private record RespuestaHttp(int codigo,String cuerpo){}
}
