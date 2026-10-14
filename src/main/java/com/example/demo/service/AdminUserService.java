package com.example.demo.service;

import com.example.demo.DTO.mapper.UserMapper;
import com.example.demo.DTO.request.UpdateRequest;
import com.example.demo.DTO.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateEmailException;
import com.example.demo.exception.DuplicateMatriculaException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;


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

    public UserResponse updateUser(Long id, UpdateRequest request){
        User user = userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);

        String normalizedEmail = EmailNormalizer.normalize(request.email());

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new DuplicateEmailException();
        }

        if (userRepository.existsByMatriculaAndIdNot(request.matricula(), id)) {
            throw new DuplicateMatriculaException();
        }

        mapper.updateEntityFromRequest(request, user);
        user.setEmail(normalizedEmail);

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
