package com.example.demo.DTO.mapper;

import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User ToEntity(UserRequest userRequest);

    UserResponse toResponse(User entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    void uddateEntityFromRequest(UserRequest request, @MappingTarget User entity);

}
