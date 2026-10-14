package com.example.demo.security.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UpdateRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.UserRepository;

import com.example.demo.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;


    @Test
    void deletarUsuarioInexistenteDeveFalhar() {

        Long id = 999L;

        when(userRepository.existsById(id))
                .thenReturn(false);

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> adminUserService.deleteById(id)
        );

        assertEquals(
                "Usuário não encontrado",
                exception.getMessage()
        );
        verify(userRepository).existsById(id);

        verify(userRepository, never())
                .deleteById(id);
    }

    @Test
    void atualizarUsuarioInexistenteDeveFalhar() {

        Long id = 999L;

        UpdateRequest request = new UpdateRequest(
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901"
        );

        when(userRepository.findById(id))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> adminUserService.updateUser(id, request)
        );

        assertEquals(
                "Usuário não encontrado",
                exception.getMessage()
        );

        verify(userRepository)
                .findById(id);

        verify(mapper, never())
                .updateEntityFromRequest(any(), any());

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void atualizarUsuarioExistenteDeveSalvarAlteracoes() {

        Long id = 999L;

        UpdateRequest request = new UpdateRequest(
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Analista",
                "12345678901"
        );

        User user = new User();
        user.setId(id);
        user.setNome("Artur Pereira");
        user.setEmail("artur@teste.com");
        user.setPassword("hash-original");

        UserResponse response = new UserResponse(
                id,
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Analista",
                "12345678901"
        );

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));


        when(userRepository.save(user))
                .thenReturn(user);

        when(mapper.toResponse(user))
                .thenReturn(response);

        UserResponse resultado = adminUserService.updateUser(id, request);

        assertEquals(response, resultado);

        assertEquals("hash-original", user.getPassword());

        verify(userRepository).findById(id);

        verify(mapper)
                .updateEntityFromRequest(request, user);

        verify(userRepository).save(user);

        verify(mapper).toResponse(user);
    }

    @Test
    void deletarUsuarioExistenteDeveExcluir(){

        Long id = 5L;

        when(userRepository.existsById(id))
                .thenReturn(true);

        adminUserService.deleteById(id);

        verify(userRepository).existsById(id);

        verify(userRepository).deleteById(id);

    }

    @Test
    void buscarUsuarioExistenteDeveRetornarDados(){

        Long id = 7L;

        User user = new User();
        user.setId(id);
        user.setNome("Artur Pereira");
        user.setEmail("artur@teste.com");

        UserResponse response = new UserResponse(
                id,
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901"
        );

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        when(mapper.toResponse(user))
                .thenReturn(response);

        Optional<UserResponse> resultado =
                adminUserService.getUserById(id);

        assertTrue(resultado.isPresent());

        assertEquals(response, resultado.orElseThrow());

        verify(userRepository).findById(id);

        verify(mapper).toResponse(user);

    }

    @Test
    void buscarUsuarioInexistenteDeveRetornarVazio(){

        Long id = 89L;

        when(userRepository.findById(id))
                .thenReturn(Optional.empty());

        Optional<UserResponse> resultado =
                adminUserService.getUserById(id);

        assertTrue(resultado.isEmpty());

        verify(userRepository).findById(id);

        verify(mapper, never())
                .toResponse(any(User.class));
    }
}

