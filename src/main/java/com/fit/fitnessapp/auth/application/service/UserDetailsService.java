package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.out.UserAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserAuthenticationPort userAuthenticationPort;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAuthenticationPort.loadUserByUsername(username);
    }
}
