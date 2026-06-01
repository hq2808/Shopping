package com.mini.shopee.service;

import com.mini.shopee.dto.JwtResponse;
import com.mini.shopee.dto.LoginRequest;
import com.mini.shopee.dto.RegisterRequest;
import com.mini.shopee.config.JwtUtils;
import com.mini.shopee.entity.Role;
import com.mini.shopee.entity.User;
import com.mini.shopee.exception.BadRequestException;
import com.mini.shopee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

        Role finalRole = request.getRole() != null ? request.getRole() : Role.ROLE_USER;
        BigDecimal finalBalance = request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO;

        if (finalBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Initial balance cannot be negative");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(finalRole)
                .balance(finalBalance)
                .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found after authentication"));

        return JwtResponse.builder()
                .token(jwt)
                .username(user.getUsername())
                .role(user.getRole().name())
                .balance(user.getBalance())
                .build();
    }
}
