package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private User save(User user) {
        return userRepository.save(user);
    }

    private List<User> getAll(){
        return userRepository.findAll();
    }

    private User getUserById(Long id) {
        return userRepository.getById(id);
    }

    private void deleteById(Long id) {
        if (!userRepository.existsById(id)){
            throw new RuntimeException("Usuário não encontrado");
        }
        userRepository.deleteById(id);

    }

}
