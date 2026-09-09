package com.tonuapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Bean de {@link JavaMailSender} construido a partir de {@link MailProperties}
 * (prefijo tonuapp.mail). No se apoya en la autoconfiguracion de spring.mail.*:
 * aqui el esquema de credenciales es propio, via variables TONUAPP_MAIL_* (o
 * application-local.yml, gitignored).
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.getPort());
        sender.setUsername(props.getUsername());
        sender.setPassword(props.getPassword());

        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.smtp.starttls.required", "true");
        mailProps.put("mail.smtp.connectiontimeout", "5000");
        mailProps.put("mail.smtp.timeout", "5000");
        mailProps.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}