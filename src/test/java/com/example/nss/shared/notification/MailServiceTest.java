package com.example.nss.shared.notification;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class MailServiceTest {
  @Test
  void sendsConfiguredTextMessage() {
    JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
    MailService service = new MailService(sender);
    ReflectionTestUtils.setField(service, "from", "nss@example.com");

    service.sendText("user@example.com", "Subject", "Body");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(captor.capture());
    org.junit.jupiter.api.Assertions.assertTrue(matches(captor.getValue()));
  }

  @Test
  void omitsBlankSenderAddress() {
    JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
    MailService service = new MailService(sender);
    ReflectionTestUtils.setField(service, "from", " ");

    service.sendText("user@example.com", "Subject", "Body");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(captor.capture());
    org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getFrom());
  }

  @Test
  void sendsOnlyAfterTheCurrentTransactionCommits() {
    JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);
    MailService service = new MailService(sender);
    TransactionSynchronizationManager.initSynchronization();

    try {
      service.sendTextAfterCommit("user@example.com", "Subject", "Body");
      org.mockito.Mockito.verifyNoInteractions(sender);

      TransactionSynchronizationUtils.invokeAfterCommit(
          TransactionSynchronizationManager.getSynchronizations());

      verify(sender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static boolean matches(SimpleMailMessage message) {
    return message.getFrom().equals("nss@example.com")
        && message.getTo()[0].equals("user@example.com")
        && message.getSubject().equals("Subject")
        && message.getText().equals("Body");
  }
}
