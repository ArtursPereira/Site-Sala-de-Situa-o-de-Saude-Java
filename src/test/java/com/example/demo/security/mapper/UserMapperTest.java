package com.example.demo.security.mapper;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UserMapperTest {

    private final UserMapper mapper
            = Mappers.getMapper(UserMapper.class);

    @Test
    void atualizarUsuarioNaoDeveAlterarRole() {

        User user = new User();

        user.setId(1L);
        user.setNome("Artur Pereira");
        user.setEmail("artur@teste.com");
        user.setCargo("Estagiario");
        user.setMatricula("12345678901");
        user.setPassword("hash-original");
        user.setRole(Role.ADMIN);

         UserRequest request = new UserRequest(
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Diretor",
                "98765432101",
                "SenhaNova123"
        );

        mapper.updateEntityFromRequest(request, user);

        assertEquals(1L, user.getId());

        assertEquals(
                Role.ADMIN,
                user.getRole()
        );

        assertEquals(
                "hash-original",
                user.getPassword()
        );

        assertEquals(
                "Artur Atualizado",
                user.getNome()
        );

        assertEquals(
                "artur.novo@teste.com",
                user.getEmail()
        );

        assertEquals(
                "Diretor",
                user.getCargo()
        );

        assertEquals(
                "98765432101",
                user.getMatricula()
        );
    }

    @Test
    void converterRequestNaoDeveDefinirRole() {

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "ADMIN",
                "12345678901",
                "Artur123"
        );

        User user = mapper.toEntity(request);

        assertNull(user.getRole());

        assertNull(user.getPassword());

        assertNull(user.getId());

        assertEquals(
                "ADMIN",
                user.getCargo()
        );

        assertEquals(
                "Artur Pereira",
                user.getNome()
        );
    }
}
