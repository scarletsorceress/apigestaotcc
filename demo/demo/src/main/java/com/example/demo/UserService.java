package com.example.demo;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User criarUser(String username, String password) {
        if (userRepository.findByUsername(username) != null) {
            throw new RuntimeException("Usuário já existe!");
        }

        User user = new User(username, encoder.encode(password));
        return userRepository.save(user);
    }

    public User validarLogin(String username, String password) {
        User user = userRepository.findByUsername(username);

        if (user == null || !encoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Credenciais inválidas!");
        }

        return user;
    }
}
