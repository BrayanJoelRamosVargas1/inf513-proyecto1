package bo.edu.uagrm.inf513.grupo09sa.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(
        String inboundProtocol, String imapHost, int imapPort, boolean imapSsl,
        String smtpHost, int smtpPort, boolean smtpSsl, boolean smtpAuth,
        String outboundMode, String roundcubeUrl,
        String mailUser, String mailAddress, String mailPassword,
        String dbUrl, String dbUser, String dbPassword,
        int pollSeconds, int connectSeconds, int readSeconds, int maxMessagesPerCycle,
        int retryMaxSeconds, String mailFolder) {

    public static AppConfig load() throws IOException {
        Properties p = new Properties();
        Path file = Path.of("config", "application.properties");
        if (Files.exists(file)) try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        return new AppConfig(
            value(p,"mail.inbound.protocol","MAIL_INBOUND_PROTOCOL","pop3"),
            value(p,"mail.inbound.host","MAIL_INBOUND_HOST","mail.tecnoweb.org.bo"),
            integer(p,"mail.inbound.port","MAIL_INBOUND_PORT",110),
            bool(p,"mail.inbound.ssl","MAIL_INBOUND_SSL",false),
            value(p,"mail.smtp.host","MAIL_SMTP_HOST","mail.tecnoweb.org.bo"),
            integer(p,"mail.smtp.port","MAIL_SMTP_PORT",25),
            bool(p,"mail.smtp.ssl","MAIL_SMTP_SSL",false),
            bool(p,"mail.smtp.auth","MAIL_SMTP_AUTH",false),
            value(p,"mail.outbound.mode","MAIL_OUTBOUND_MODE","roundcube"),
            value(p,"mail.roundcube.url","MAIL_ROUNDCUBE_URL","https://mail.tecnoweb.org.bo/roundcubemail/"),
            value(p,"mail.username","MAIL_USERNAME","grupo09sa"),
            value(p,"mail.address","MAIL_ADDRESS","grupo09sa@tecnoweb.org.bo"),
            required(p,"mail.password","MAIL_PASSWORD"),
            value(p,"db.url","DB_URL","jdbc:postgresql://db.tecnoweb.org.bo:5432/db_grupo09sa"),
            value(p,"db.username","DB_USERNAME","grupo09sa"),
            required(p,"db.password","DB_PASSWORD"),
            integer(p,"poll.seconds","POLL_SECONDS",10),
            integer(p,"network.connect.seconds","NETWORK_CONNECT_SECONDS",10),
            integer(p,"network.read.seconds","NETWORK_READ_SECONDS",30),
            integer(p,"mail.max.messages.per.cycle","MAIL_MAX_MESSAGES_PER_CYCLE",25),
            integer(p,"retry.max.seconds","RETRY_MAX_SECONDS",300),
            value(p,"mail.folder","MAIL_FOLDER","INBOX"));
    }

    private static String required(Properties p, String key, String env) {
        String v=value(p,key,env,null); if(v==null||v.isBlank()) throw new IllegalStateException("Falta configurar " + env); return v;
    }
    private static String value(Properties p,String key,String env,String def){String e=System.getenv(env); if(e!=null&&!e.isBlank())return e; String v=p.getProperty(key); return v==null||v.isBlank()?def:v.trim();}
    private static int integer(Properties p,String key,String env,int def){return Integer.parseInt(value(p,key,env,String.valueOf(def)));}
    private static boolean bool(Properties p,String key,String env,boolean def){return Boolean.parseBoolean(value(p,key,env,String.valueOf(def)));}
}
