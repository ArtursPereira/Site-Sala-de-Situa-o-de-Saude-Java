package com.example.demo.security.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.DuplicateMatriculaException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import jakarta.validation.executable.ValidateOnExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void cadastroDeveCriarUsuarioComRoleUser() {

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "ADMIN",
                "12345678901",
                "Artur123"
        );


        User user = new User();

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(userRepository.existsByMatricula(request.matricula()))
                .thenReturn(false);

        when(mapper.toEntity(request))
                .thenReturn(user);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("senha-criptografada");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.save(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User usuarioSalvo = captor.getValue();

        assertEquals(
                Role.USER,
                usuarioSalvo.getRole()
        );
    }

    @Test
    void cadastroDeveCriptografarSenha() {

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901",
                "Artur123"
        );

        User user = new User();

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(userRepository.existsByMatricula(request.matricula()))
                .thenReturn(false);

        when(mapper.toEntity(request))
                .thenReturn(user);

        when(passwordEncoder.encode("Artur123"))
                .thenReturn("senha-criptografada");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.save(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(captor.capture());

        User usuarioSalvo = captor.getValue();

        assertEquals(
                "senha-criptografada",
                usuarioSalvo.getPassword()
        );

        assertNotEquals(
                request.password(),
                usuarioSalvo.getPassword()
        );

        verify(passwordEncoder)
                .encode("Artur123");
    }

    @Test
    void cadastroComEmailDuplicadoDeveFalhar() {

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901",
                "Artur123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                DuplicateEmailException.class,
                () -> userService.save(request)
        );

        assertEquals(
                "E-mail já cadastrado",
                exception.getMessage()
        );

        verify(userRepository, never())
                .save(any(User.class));

        verify(mapper, never())
                .toEntity(any());

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    @Test
    void cadastroComMatriculaDuplicadaDeveFalhar() {

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901",
                "Artur123"
        );

        when(userRepository.existsByMatricula(request.matricula()))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                DuplicateMatriculaException.class,
                () -> userService.save(request)
        );

        assertEquals(
                "Matrícula já cadastrada",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByEmail(request.email());

        verify(userRepository)
                .existsByMatricula(request.matricula());

        verify(userRepository, never())
                .save(any(User.class));

        verify(mapper, never())
                .toEntity(any());

        verify(passwordEncoder, never())
                .encode(anyString());


    }

    @Test
    void deletarUsuarioInexistenteDeveFalhar() {

        Long id = 999L;

        when(userRepository.existsById(id))
                .thenReturn(false);

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.deleteById(id)
        );

        assertEquals(
                "Usuário não encontrado",
                exception.getMessage()
        );

        verify(userRepository, never())
                .deleteById(id);
    }

    @Test
    void atualizarUsuarioInexistenteDeveFalhar() {

        Long id = 999L;

        UserRequest request = new UserRequest(
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901",
                "Artur123"
        );

        when(userRepository.findById(id))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.updateUser(id, request)
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

        UserRequest request = new UserRequest(
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Analista",
                "12345678901",
                "SenhaNova123"
        );

        User user = new User();
        user.setId(id);
        user.setNome("Artur Pereira");
        user.setEmail("artur@teste.com");
        user.setPassword("hash-antigo");

        UserResponse response = new UserResponse(
                id,
                "Artur Atualizado",
                "artur.novo@teste.com",
                "Analista",
                "12345678901"
        );

        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode(request.password()))
                .thenReturn("hash-novo");

        when(userRepository.save(user))
                .thenReturn(user);

        when(mapper.toResponse(user))
                .thenReturn(response);

        UserResponse resultado = userService.updateUser(id, request);

        assertEquals(response, resultado);

        assertEquals("hash-novo", user.getPassword());

        verify(userRepository).findById(id);

        verify(mapper)
                .updateEntityFromRequest(request, user);

        verify(passwordEncoder)
                .encode(request.password());

        verify(userRepository).save(user);

        verify(mapper).toResponse(user);
    }

    @Test
    void deletarUsuarioExistenteDeveExcluir(){

        Long id = 5L;

        when(userRepository.existsById(id))
                .thenReturn(true);

        userService.deleteById(id);

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
                userService.getUserById(id);

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
                userService.getUserById(id);

        assertTrue(resultado.isEmpty());

        verify(userRepository).findById(id);

        verify(mapper, never())
                .toResponse(any(User.class));
    }



}
