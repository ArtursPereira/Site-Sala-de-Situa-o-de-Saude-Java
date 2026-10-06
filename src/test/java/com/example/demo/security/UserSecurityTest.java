package com.example.demo.security;

import com.example.demo.DTO.response.UserResponse;
import com.example.demo.controller.UserController;
import com.example.demo.service.UserService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
class UserSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(
            username = "usuario@teste.com",
            roles = "USER"
    )
    void userNaoDeveConseguirListarUsuarios() throws Exception {

        mockMvc.perform(
                        get("/NSS/users")
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    @WithMockUser(
            username = "admin@teste.com",
            roles = "ADMIN"
    )
    void adminDeveConseguirListarUsuarios() throws Exception {

        when(userService.getAll())
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/NSS/users")
                )
                .andExpect(
                        status().isOk()
                );
        verify(userService).getAll();
    }

    @Test
    @WithMockUser(
            username = "usuario@teste.com",
            roles = "USER"
    )
    void usuarioNaoDeveConseguirDeletarUsuarios() throws Exception {

        mockMvc.perform(
                delete("/NSS/users/1")
        ).andExpect(
                status().isForbidden()
        );

        verify(userService, never()).deleteById(1L);
    }

    @Test
    @WithMockUser(
            username = "admin@teste.com",
            roles = "ADMIN"
    )
    void adminDeveConseguirDeletarUsuarios() throws Exception{


        mockMvc.perform(
                delete("/NSS/users/1")
        ).andExpect
                (status().isNoContent());
        verify(userService).deleteById(1L);
    }
    @Test
    @WithMockUser(
            username = "aluno@teste.com",
            roles = "USER"
    )
    void usuarioNaoDeveConseguirListarUsuario() throws Exception {

        mockMvc.perform(
                get("/NSS/users/1")
        ).andExpect(status().isForbidden());

        verify(userService, never()).getUserById(1L);
    }

    @Test
    @WithMockUser(
            username = "admin@teste.com",
            roles = "ADMIN"
    )
    void adminDeveConseguirListarUsuario() throws Exception{

        UserResponse response = new UserResponse(
                1L,
                "Artur Pereira",
                "artur@teste.com",
                "Estagiario",
                "12345678901"
        );

        when(userService.getUserById(1L))
                .thenReturn(Optional.of(response));

        mockMvc.perform(
                get("/NSS/users/1")
        ).andExpect(status().isOk());
        verify(userService).getUserById(1L);
    }

    @Test
    @WithMockUser(
            username = "usuario@teste.com",
            roles = "USER"
    )
    void usuarioNaoDeveConseguirAtualizarUsuario() throws Exception {
        mockMvc.perform(
                put("/NSS/users/1")
        ).andExpect(status().isForbidden());

        verify(userService, never())
                .updateUser(anyLong(), any());
    }
    @Test
    @WithMockUser(
            username = "admin@teste.com",
            roles = "ADMIN"
    )
    void adminDeveConseguirAtualizarUsuario() throws Exception {

        UserResponse response = new UserResponse(
                1L,
                "Artur Atualizado",
                "artur@teste.com",
                "Estagiario",
                "12345678901"
        );

        when(userService.updateUser(eq(1L), any()))
                .thenReturn(response);

        String json = """
            {
              "nome": "Artur Atualizado",
              "email": "artur@teste.com",
              "cargo": "Estagiario",
              "matricula": "12345678901",
              "password": "Artur123"
            }
            """;

        mockMvc.perform(
                put("/NSS/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                status().isOk()
        );

        verify(userService)
                .updateUser(eq(1L), any());
    }

    @Test
    void usuarioNaoAutenticadoNaoDeveConseguirListarUsuarios() throws Exception{
        mockMvc.perform(
                get("/NSS/users")
        ).andExpect(status().isUnauthorized());
    }
}