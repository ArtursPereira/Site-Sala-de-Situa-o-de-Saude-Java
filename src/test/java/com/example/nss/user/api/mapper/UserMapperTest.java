package com.example.nss.user.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.nss.user.domain.AccountType;
import com.example.nss.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserMapperTest {

  private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

  @Test
  void mapsUserToPublicResponse() {
    User user = user();

    var response = mapper.toResponse(user);

    assertEquals(user.getId(), response.id());
    assertEquals(user.getNome(), response.nome());
    assertEquals(user.getEmail(), response.email());
    assertTrue(response.ativo());
  }

  @Test
  void mapsUserToManagedResponse() {
    User user = user();

    var response = mapper.toManagedResponse(user, true);

    assertEquals(user.getId(), response.id());
    assertEquals(user.getNome(), response.name());
    assertEquals(user.getAccountType(), response.accountType());
    assertTrue(response.active());
    assertTrue(response.firstAccessPending());
  }

  private User user() {
    User user = new User();
    user.setId(7L);
    user.setNome("Ana Saúde");
    user.setEmail("ana@example.com");
    user.setAccountType(AccountType.ADMIN);
    user.setAtivo(true);
    return user;
  }
}
