package com.example.demo.security;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void deveNormalizarEmailECarregarRole() {
        User user = new User();
        user.setEmail("artur@teste.com");
        user.setPassword("hash");
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmailIgnoreCase("artur@teste.com"))
                .thenReturn(Optional.of(user));

        UserDetails result = new CustomUserDetailsService(userRepository)
                .loadUserByUsername("  Artur@Teste.COM ");

        assertEquals("artur@teste.com", result.getUsername());
        assertEquals("ROLE_ADMIN", result.getAuthorities().iterator().next().getAuthority());
        verify(userRepository).findByEmailIgnoreCase("artur@teste.com");
    }

    @Test
    void usuarioInexistenteDeveFalhar() {
        when(userRepository.findByEmailIgnoreCase("inexistente@teste.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> new CustomUserDetailsService(userRepository)
                        .loadUserByUsername("inexistente@teste.com")
        );
    }
}
