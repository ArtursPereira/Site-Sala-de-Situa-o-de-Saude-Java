package com.example.demo.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UserRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;

    public UserResponse save(UserRequest request) {
        User user = mapper.ToEntity(request);
        return mapper.toResponse(userRepository.save(user));
    }

    public List<UserResponse> getAll(){
        return userRepository.findAll()
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    public Optional<UserResponse> getUserById(Long id) {

        return userRepository.findById(id)
                .map(mapper::toResponse);
    }

    public UserResponse updateUser(Long id, UserRequest request){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        mapper.uddateEntityFromRequest(request, user);
        return mapper.toResponse(userRepository.save(user));
    }

    public void deleteById(Long id) {
        if (!userRepository.existsById(id)){
            throw new RuntimeException("Usuário não encontrado");
        }
        userRepository.deleteById(id);

    }

}
