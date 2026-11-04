package com.example.nss.user.api.mapper;

import com.example.nss.user.api.response.ManagedUserResponse;
import com.example.nss.user.api.response.UserResponse;
import com.example.nss.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserResponse toResponse(User user);

  @Mapping(target = "name", source = "user.nome")
  @Mapping(target = "active", source = "user.ativo")
  @Mapping(target = "firstAccessPending", source = "firstAccessPending")
  ManagedUserResponse toManagedResponse(User user, boolean firstAccessPending);
}
