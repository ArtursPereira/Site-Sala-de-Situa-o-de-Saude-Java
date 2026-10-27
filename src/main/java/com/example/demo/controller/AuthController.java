package com.example.demo.controller;

import com.example.demo.DTO.request.LoginRequest;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.LoginResponse;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.security.EmailNormalizer;
import com.example.demo.security.JwtService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request){
        UserResponse response = userService.save(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        String normalizedEmail =
                EmailNormalizer.normalize(request.email());


        Authentication authenticationRequest =
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.password()
                );

        Authentication authenticationResult =
                authenticationManager.authenticate(authenticationRequest);

        if (authenticationResult == null
                || !(authenticationResult.getPrincipal()
                instanceof UserDetails authenticatedUser)) {

            throw new AuthenticationServiceException(
                    "A autenticação não retornou um usuário válido"
            );
        }

        String token = jwtService.generateToken(authenticatedUser);

        return ResponseEntity.ok(new LoginResponse(token));

    }

}
