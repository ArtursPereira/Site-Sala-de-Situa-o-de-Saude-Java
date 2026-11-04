package com.example.nss.shared.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de envio de e-mails transacionais.
 *
 * <p>A credencial SMTP é fornecida exclusivamente pela configuração do ambiente. Tokens de convite
 * e senhas nunca devem ser registrados em logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

  private final JavaMailSender mailSender;

  @Value("${api.mail.from:}")
  private String from;

  public void sendText(String recipient, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    if (from != null && !from.isBlank()) {
      message.setFrom(from);
    }
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }

  public void sendTextAfterCommit(String recipient, String subject, String body) {
    Runnable send =
        () -> {
          try {
            sendText(recipient, subject, body);
          } catch (RuntimeException exception) {
            // A mail outage must not roll back a committed user/invitation.
            // The pending token remains available for an operational retry.
            log.warn("Falha ao enviar notificação de e-mail após commit", exception);
          }
        };
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      send.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            send.run();
          }
        });
  }
}
