package com.mini.shopee.config;

import com.mini.shopee.entity.Product;
import com.mini.shopee.entity.Role;
import com.mini.shopee.entity.User;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeUsers();
        initializeProducts();
    }

    private void initializeUsers() {
        if (userRepository.count() == 0) {
            log.info("Initializing default accounts...");

            // Default User: username='user', password='password', role=ROLE_USER, balance=1000
            User user = User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("password"))
                    .email("user@minishopee.com")
                    .role(Role.ROLE_USER)
                    .balance(new BigDecimal("1000.00"))
                    .build();

            // Default Admin: username='admin', password='adminpassword', role=ROLE_ADMIN, balance=0
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("adminpassword"))
                    .email("admin@minishopee.com")
                    .role(Role.ROLE_ADMIN)
                    .balance(BigDecimal.ZERO)
                    .build();

            userRepository.saveAll(Arrays.asList(user, admin));
            log.info("Successfully created default accounts: 'user' (pass: 'password', balance: 1000) and 'admin' (pass: 'adminpassword')");
        }
    }

    private void initializeProducts() {
        if (productRepository.count() == 0) {
            log.info("Initializing default products...");

            Product p1 = Product.builder()
                    .name("iPhone 15 Pro")
                    .description("Apple iPhone 15 Pro 256GB Titanium")
                    .price(new BigDecimal("1200.00"))
                    .stock(10)
                    .build();

            Product p2 = Product.builder()
                    .name("iPad Air")
                    .description("Apple iPad Air 5th Gen M1 Wi-Fi")
                    .price(new BigDecimal("600.00"))
                    .stock(5)
                    .build();

            Product p3 = Product.builder()
                    .name("AirPods Pro")
                    .description("Apple AirPods Pro 2nd Gen USB-C")
                    .price(new BigDecimal("250.00"))
                    .stock(20)
                    .build();

            productRepository.saveAll(Arrays.asList(p1, p2, p3));
            log.info("Successfully created default products!");
        }
    }
}
