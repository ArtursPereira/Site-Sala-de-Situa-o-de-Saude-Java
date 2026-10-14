package com.example.demo.controller;

import com.example.demo.DTO.request.UpdateRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/NSS/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<UserResponse>>  getAllUser(){
        return ResponseEntity.ok(adminUserService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return adminUserService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,  @Valid @RequestBody UpdateRequest request){
        return ResponseEntity.ok(adminUserService.updateUser(id,request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserById(@PathVariable Long id){
        adminUserService.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
