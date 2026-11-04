package com.example.nss.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {
  @Test
  void trimsAndLowercasesEmail() {
    assertEquals("ana@example.com", EmailNormalizer.normalize("  Ana@Example.COM "));
  }

  @Test
  void preservesNull() {
    assertNull(EmailNormalizer.normalize(null));
  }
}
