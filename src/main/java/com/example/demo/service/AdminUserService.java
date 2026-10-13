package com.example.demo.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;


    public List<UserResponse> getAll(){
        return userRepository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public Optional<UserResponse> getUserById(Long id) {

        return userRepository.findById(id)
                .map(mapper::toResponse);
    }

    public UserResponse updateUser(Long id, UserRequest request){
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        mapper.updateEntityFromRequest(request, user);
        user.setPassword(
                passwordEncoder.encode(request.password())
        );

        User updatedUser = userRepository.save(user);

        return mapper.toResponse(updatedUser);
    }

    public void deleteById(Long id){
        if (!userRepository.existsById(id)){
            throw new UserNotFoundException();
        }
        userRepository.deleteById(id);

    }
}
