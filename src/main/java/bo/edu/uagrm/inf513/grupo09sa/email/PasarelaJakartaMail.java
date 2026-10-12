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
    @Override public List<String> identificadores()throws Exception{if(cfg.inboundProtocol().equalsIgnoreCase("roundcube"))return roundcube.identificadores();if(cfg.inboundProtocol().equalsIgnoreCase("pop3"))return pop3.identificadores();abrir();List<String> out=new ArrayList<>();for(Message m:folder.getMessages()){String[] h=m.getHeader("Message-ID");out.add(h!=null&&h.length>0?h[0]:Integer.toString(m.getMessageNumber()));}return out;}
    @Override public List<MensajeEntrada> noProcesados(java.util.function.Predicate<String> yaProcesado)throws Exception{if(cfg.inboundProtocol().equalsIgnoreCase("roundcube"))return roundcube.nuevos(yaProcesado);if(cfg.inboundProtocol().equalsIgnoreCase("pop3"))return pop3.nuevos(yaProcesado);abrir();List<MensajeEntrada> out=new ArrayList<>();Message[] encontrados=folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false));for(Message m:encontrados){String[] h=m.getHeader("Message-ID");String id=h!=null&&h.length>0?h[0]:Integer.toString(m.getMessageNumber());if(yaProcesado.test(id))continue;String from=((InternetAddress)m.getFrom()[0]).getAddress();mensajes.put(id,m);out.add(new MensajeEntrada(id,from,m.getSubject()));}return out;}
    @Override public void responder(MensajeEntrada original,String asunto,String cuerpo)throws Exception{if(cfg.outboundMode().equalsIgnoreCase("roundcube")){roundcube.enviar(original.remitente(),asunto,cuerpo);return;}MimeMessage m=new MimeMessage(session);m.setFrom(new InternetAddress(cfg.mailAddress(),"INF513 Grupo 09 SA"));m.setRecipients(Message.RecipientType.TO,InternetAddress.parse(original.remitente()));m.setSubject(asunto,"UTF-8");m.setText(cuerpo,"UTF-8");Transport.send(m);}
    @Override public void marcarProcesado(MensajeEntrada mensaje)throws Exception{Message m=mensajes.remove(mensaje.id());if(m!=null)m.setFlag(Flags.Flag.SEEN,true);}
    @Override public void close()throws Exception{if(folder!=null&&folder.isOpen())folder.close(false);if(store!=null&&store.isConnected())store.close();roundcube.close();}
}
