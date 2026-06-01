package com.mini.shopee.service;

import com.mini.shopee.dto.OrderItemRequest;
import com.mini.shopee.dto.OrderRequest;
import com.mini.shopee.entity.Product;
import com.mini.shopee.entity.Role;
import com.mini.shopee.entity.User;
import com.mini.shopee.exception.BadRequestException;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private User testUser;
    private Product product1;
    private Product product2;

    @BeforeEach
    public void setup() {
        productRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .username("tester")
                .password("password")
                .email("tester@test.com")
                .role(Role.ROLE_USER)
                .balance(new BigDecimal("100.00"))
                .build();
        userRepository.save(testUser);

        product1 = Product.builder()
                .name("Product 1")
                .description("Product 1 description")
                .price(new BigDecimal("30.00"))
                .stock(5)
                .build();

        product2 = Product.builder()
                .name("Product 2")
                .description("Product 2 description")
                .price(new BigDecimal("40.00"))
                .stock(0) // Out of stock
                .build();

        productRepository.saveAll(Arrays.asList(product1, product2));
    }

    @Test
    public void testOrderTransactionRollback_WhenOneProductOutOfStock() {
        // Arrange
        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(product1.getId())
                .quantity(2) // Valid: product1 has stock 5
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(product2.getId())
                .quantity(1) // Invalid: product2 has stock 0
                .build();

        OrderRequest request = OrderRequest.builder()
                .items(Arrays.asList(item1, item2))
                .build();

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            orderService.createOrder("tester", request);
        });

        assertTrue(exception.getMessage().contains("out of stock"));

        // Verify Rollback: Product 1 stock must STILL be 5 (not deducted!)
        Product updatedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        assertEquals(5, updatedProduct1.getStock());

        // Verify Rollback: User balance must STILL be 100.00 (not deducted!)
        User updatedUser = userRepository.findByUsername("tester").orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(updatedUser.getBalance()));
    }
}
