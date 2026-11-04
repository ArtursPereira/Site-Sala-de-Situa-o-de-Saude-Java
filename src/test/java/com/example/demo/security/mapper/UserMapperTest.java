package com.example.demo.security.mapper;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UpdateRequest;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.entity.AccountSetupStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.junit.jupiter.api.Assertions.*;

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

         UpdateRequest request = new UpdateRequest(
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Diretor",
                "98765432101"
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

    @Test
    void atualizarDadosCadastraisDevePreservarEstadoDeAcesso() {
        User user = new User();
        user.setId(10L);
        user.setNome("Artur Pereira");
        user.setEmail("artur@teste.com");
        user.setCargo("Estagiario");
        user.setMatricula("12345678901");

        user.setPassword("hash-original");
        user.setRole(Role.ADMIN);
        user.setAtivo(false);
        user.setSetupStatus(AccountSetupStatus.PENDING);

        UpdateRequest request = new UpdateRequest(
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Analista",
                "10987654321"
        );

        mapper.updateEntityFromRequest(request, user);

        assertAll(
                // Os dados cadastrais devem mudar.
                () -> assertEquals("Artur Atualizado", user.getNome()),
                () -> assertEquals("artur.novo@teste.com", user.getEmail()),
                () -> assertEquals("Analista", user.getCargo()),
                () -> assertEquals("10987654321", user.getMatricula()),

                // Identidade, credenciais e acesso devem ser preservados.
                () -> assertEquals(Long.valueOf(10L), user.getId()),
                () -> assertEquals("hash-original", user.getPassword()),
                () -> assertEquals(Role.ADMIN, user.getRole()),
                () -> assertFalse(user.isAtivo()),
                () -> assertEquals(
                        AccountSetupStatus.PENDING,
                        user.getSetupStatus()
                )
        );
    }
}
