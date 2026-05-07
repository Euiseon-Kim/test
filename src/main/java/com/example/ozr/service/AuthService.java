package com.example.ozr.service;

import com.example.ozr.dto.LoginRequest;
import com.example.ozr.dto.LoginResponse;
import com.example.ozr.dto.UserInfo;
import com.example.ozr.model.User;
import com.example.ozr.repository.UserRepository;
import com.example.ozr.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUserId(), request.getPassword())
        );

        String userId = auth.getName();
        User user = userRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        String token = tokenProvider.generateToken(userId);
        return new LoginResponse(token, user.getUserId(), user.getName(), user.getDepartment());
    }

    public UserInfo getCurrentUser(String userId) {
        User user = userRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return new UserInfo(user.getUserId(), user.getName(), user.getDepartment());
    }
}
