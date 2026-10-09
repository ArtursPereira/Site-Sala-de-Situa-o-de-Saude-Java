package com.example.demo.security.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.DuplicateMatriculaException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

}
