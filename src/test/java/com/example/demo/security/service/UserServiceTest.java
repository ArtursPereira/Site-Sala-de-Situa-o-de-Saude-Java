package com.example.demo.security.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
