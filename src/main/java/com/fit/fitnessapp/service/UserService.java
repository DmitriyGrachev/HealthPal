package com.fit.fitnessapp.service;

import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import com.fit.fitnessapp.model.dto.RegisterRequest;
import com.fit.fitnessapp.model.user.Role;
import com.fit.fitnessapp.model.user.User;
import com.fit.fitnessapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User findUserByUsernameAndEmail(RegisterRequest registerRequest){
            return userRepository.findUserByUsernameAndEmail(registerRequest.getUsername(),registerRequest.getEmail())
                    .orElse(null);
    }
    public void registerUser(RegisterRequest registerRequest){
        if(userRepository.existsUserByUsername(registerRequest.getUsername())){
            throw new UserAlreadyExistsException("User with such username already exists");
        }
        if(userRepository.existsUserByEmail(registerRequest.getEmail())){
            throw new UserAlreadyExistsException("User with such email already exists");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setRoles(Set.of(Role.USER));
        userRepository.save(user);
    }

    public void saveUser(User user){
        userRepository.save(user);
    }
}
