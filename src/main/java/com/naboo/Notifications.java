package com.naboo;

import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
class Notifications {
    private static final Logger log=LoggerFactory.getLogger(Notifications.class);
    private final String host, username, password, from, recipient;
    private final int port;
    private final Settings settings;
    Notifications(@Value("${MAIL_HOST:}") String host,@Value("${MAIL_PORT:587}") int port,
        @Value("${MAIL_USERNAME:}") String username,@Value("${MAIL_PASSWORD:}") String password,
        @Value("${MAIL_FROM:}") String from,@Value("${MAIL_TO:}") String recipient,Settings settings) {
        this.host=host;this.port=port;this.username=username;this.password=password;this.from=from;this.recipient=recipient;this.settings=settings;
    }
    void newRequest(String type,String reference,String customerEmail) {
        String internal=settings.findById("private.contactRecipient").map(s->s.value).filter(v->!v.isBlank()).orElse(recipient);
        send(internal,"NABOO: new "+type+" request "+reference,"A new "+type+" request has been received. Sign in to the NABOO admin dashboard to review reference "+reference+".",reference);
        send(customerEmail,"NABOO request received ("+reference+")","Thank you for contacting NABOO Technologies. We have received your "+type+" request. Your reference number is "+reference+".",reference);
    }
    void demoStatus(String email,String reference,String status) {
        if(!List.of("APPROVED","RESCHEDULED").contains(status)) return;
        send(email,"NABOO demo request "+status.toLowerCase()+" ("+reference+")","Your demo request "+reference+" has been "+status.toLowerCase()+". NABOO will contact you with any remaining details.",reference);
    }
    void orderStatus(String email,String reference,String status) {
        if(!List.of("PAID","CANCELLED","REFUNDED").contains(status)) return;
        send(email,"NABOO order "+reference+" "+status.toLowerCase(),"Your order "+reference+" is now marked as "+status.toLowerCase().replace('_',' ')+". Contact NABOO if you have any questions.",reference);
    }
    private void send(String to,String subject,String text,String reference) {
        if(host.isBlank() || from.isBlank() || to==null || to.isBlank()) return;
        JavaMailSenderImpl sender=new JavaMailSenderImpl();sender.setHost(host);sender.setPort(port);
        if(!username.isBlank()) { sender.setUsername(username);sender.setPassword(password); }
        Properties properties=sender.getJavaMailProperties();properties.put("mail.smtp.auth",String.valueOf(!username.isBlank()));
        properties.put("mail.smtp.starttls.enable","true");properties.put("mail.smtp.connectiontimeout","3000");properties.put("mail.smtp.timeout","3000");
        SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);
        message.setSubject(subject);message.setText(text);
        try { sender.send(message); } catch (MailException exception) { log.warn("Optional mail delivery failed for reference {}",reference); }
    }
}
