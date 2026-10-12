package bo.edu.uagrm.inf513.grupo09sa.email;

import bo.edu.uagrm.inf513.grupo09sa.config.AppConfig;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.eclipse.angus.mail.pop3.POP3Folder;
import java.util.*;

public final class PasarelaJakartaMail implements PasarelaCorreo {
    private final AppConfig cfg; private final Session session; private final ClienteRoundcube roundcube; private final ClientePop3 pop3; private Store store; private Folder folder; private final Map<String,Message> mensajes=new HashMap<>();
    public PasarelaJakartaMail(AppConfig cfg){this.cfg=cfg;Properties p=new Properties();p.put("mail.smtp.host",cfg.smtpHost());p.put("mail.smtp.port",cfg.smtpPort());p.put("mail.smtp.auth",cfg.smtpAuth());p.put("mail.smtp.ssl.enable",cfg.smtpSsl());p.put("mail."+cfg.inboundProtocol()+".ssl.enable",cfg.imapSsl());session=Session.getInstance(p,new Authenticator(){protected PasswordAuthentication getPasswordAuthentication(){return new PasswordAuthentication(cfg.mailUser(),cfg.mailPassword());}});roundcube=new ClienteRoundcube(cfg);pop3=new ClientePop3(cfg);}
    private void abrir()throws Exception{if(store!=null&&store.isConnected()&&folder!=null&&folder.isOpen())return;store=session.getStore(cfg.inboundProtocol());store.connect(cfg.imapHost(),cfg.imapPort(),cfg.mailUser(),cfg.mailPassword());folder=store.getFolder(cfg.mailFolder());folder.open(Folder.READ_WRITE);}
    @Override public List<String> identificadores()throws Exception{if(cfg.inboundProtocol().equalsIgnoreCase("roundcube"))return roundcube.identificadores();if(cfg.inboundProtocol().equalsIgnoreCase("pop3"))return pop3.identificadores();abrir();List<String> out=new ArrayList<>();for(Message m:folder.getMessages())out.add(idMensaje(m));return out;}
    @Override public List<MensajeEntrada> noProcesados(ConsultaProcesados consulta)throws Exception{if(cfg.inboundProtocol().equalsIgnoreCase("roundcube"))return roundcube.nuevos(consulta);if(cfg.inboundProtocol().equalsIgnoreCase("pop3"))return pop3.nuevos(consulta);abrir();List<MensajeEntrada> out=new ArrayList<>();Message[] encontrados=folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false));for(int inicio=0;inicio<encontrados.length&&out.size()<cfg.maxMessagesPerCycle();inicio+=500){int fin=Math.min(encontrados.length,inicio+500);List<String> ids=new ArrayList<>(fin-inicio);for(int i=inicio;i<fin;i++)ids.add(idMensaje(encontrados[i]));Set<String> conocidos=consulta.conocidos(ids);for(int i=inicio;i<fin&&out.size()<cfg.maxMessagesPerCycle();i++){Message m=encontrados[i];String id=ids.get(i-inicio);if(conocidos.contains(id))continue;try{Address[] origen=m.getFrom();String from=origen!=null&&origen.length>0&&origen[0] instanceof InternetAddress a?a.getAddress():"";mensajes.put(id,m);out.add(new MensajeEntrada(id,from,m.getSubject()));}catch(MessagingException correoInvalido){if(store==null||!store.isConnected()||folder==null||!folder.isOpen())throw correoInvalido;out.add(new MensajeEntrada(id,"","CORREO MALFORMADO"));}catch(RuntimeException correoInvalido){out.add(new MensajeEntrada(id,"","CORREO MALFORMADO"));}}}return out;}
    private String idMensaje(Message mensaje)throws MessagingException{
        String[] cabecera=mensaje.getHeader("Message-ID");String candidato=cabecera!=null&&cabecera.length>0?cabecera[0]:null;
        if(candidato!=null&&!candidato.isBlank()&&candidato.length()<=255&&candidato.chars().noneMatch(Character::isISOControl))return candidato;
        if(folder instanceof UIDFolder uidFolder){long uid=uidFolder.getUID(mensaje);if(uid>=0)return "imap:"+cfg.mailFolder()+":"+uid;}
        return "mensaje:"+mensaje.getMessageNumber();
    }
    @Override public void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception{if(cfg.outboundMode().equalsIgnoreCase("roundcube")){roundcube.enviar(original.remitente(),asunto,cuerpo);return;}MimeMessage m=new MimeMessage(session);m.setFrom(new InternetAddress(cfg.mailAddress(),"INF513 Grupo 09 SA"));m.setRecipients(Message.RecipientType.TO,InternetAddress.parse(original.remitente()));m.setSubject(asunto,"UTF-8");m.setText(cuerpo,"UTF-8");Transport.send(m);}
    @Override public void marcarProcesado(MensajeEntrada mensaje)throws Exception{Message m=mensajes.remove(mensaje.id());if(m!=null)m.setFlag(Flags.Flag.SEEN,true);}
    @Override public void close()throws Exception{if(folder!=null&&folder.isOpen())folder.close(false);if(store!=null&&store.isConnected())store.close();roundcube.close();}
}
