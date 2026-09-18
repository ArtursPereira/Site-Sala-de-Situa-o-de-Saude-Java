package com.example.demo.controller;

import com.example.demo.DTO.request.LoginRequest;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request){
        UserResponse response = userService.save(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request
    ) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                );

        authenticationManager.authenticate(authentication);
        return ResponseEntity.ok("Login realizado com sucesso");

    }

}
