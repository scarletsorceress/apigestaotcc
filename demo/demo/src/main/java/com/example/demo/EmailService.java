package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender; // aqui você declarou

    public void enviarEmail(String para, String assunto, String corpo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(corpo);
        mailSender.send(mensagem); // use o mesmo nome aqui
    }

    public void enviarNotificacao(String remetente, String texto) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("gv005070@email.com"); // e-mail do orientador
        message.setSubject("Nova mensagem de TCC");
        message.setText("Mensagem de: " + remetente + "\n\n" + texto);

        mailSender.send(message); // aqui também deve ser mailSender
    }
}
