package com.mini.shopee.service;

import com.mini.shopee.BaseIntegrationTest;
import com.mini.shopee.dto.LoginRequest;
import com.mini.shopee.dto.RegisterRequest;
import com.mini.shopee.dto.JwtResponse;
import com.mini.shopee.entity.Role;
import com.mini.shopee.entity.User;
import com.mini.shopee.exception.BadRequestException;
import com.mini.shopee.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceIT extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    public void testRegister_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("auth_tester")
                .password("password123")
                .email("auth@test.com")
                .role(Role.ROLE_USER)
                .initialBalance(new BigDecimal("250.50"))
                .build();

        User registeredUser = authService.register(request);

        assertNotNull(registeredUser);
        assertNotNull(registeredUser.getId());
        assertEquals("auth_tester", registeredUser.getUsername());
        assertEquals("auth@test.com", registeredUser.getEmail());
        assertEquals(Role.ROLE_USER, registeredUser.getRole());
        assertEquals(0, new BigDecimal("250.50").compareTo(registeredUser.getBalance()));

        // Verify password is encrypted
        assertNotEquals("password123", registeredUser.getPassword());
        assertTrue(registeredUser.getPassword().startsWith("$2a$")); // BCrypt prefix
    }

    @Test
    public void testRegister_DuplicateUsername_ThrowsBadRequest() {
        RegisterRequest request1 = RegisterRequest.builder()
                .username("dup_tester")
                .password("password123")
                .email("dup1@test.com")
                .build();
        authService.register(request1);

        RegisterRequest request2 = RegisterRequest.builder()
                .username("dup_tester")
                .password("password456")
                .email("dup2@test.com")
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            authService.register(request2);
        });

        assertEquals("Username is already taken", exception.getMessage());
    }

    @Test
    public void testLogin_Success() {
        RegisterRequest regRequest = RegisterRequest.builder()
                .username("login_tester")
                .password("mypassword")
                .email("login@test.com")
                .role(Role.ROLE_ADMIN)
                .initialBalance(BigDecimal.ZERO)
                .build();
        authService.register(regRequest);

        LoginRequest loginRequest = LoginRequest.builder()
                .username("login_tester")
                .password("mypassword")
                .build();

        JwtResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("login_tester", response.getUsername());
        assertEquals("ROLE_ADMIN", response.getRole());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getBalance()));
    }

    @Test
    public void testLogin_InvalidCredentials_ThrowsBadRequest() {
        RegisterRequest regRequest = RegisterRequest.builder()
                .username("wrong_tester")
                .password("correct_pass")
                .email("wrong@test.com")
                .build();
        authService.register(regRequest);

        LoginRequest loginRequest = LoginRequest.builder()
                .username("wrong_tester")
                .password("incorrect_pass")
                .build();

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> {
            authService.login(loginRequest);
        });
    }
}
