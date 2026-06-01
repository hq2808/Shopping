package com.mini.shopee;

import com.mini.shopee.dto.*;
import com.mini.shopee.entity.*;
import com.mini.shopee.repository.OrderRepository;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import com.mini.shopee.service.RedisSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShopeeIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RedisSyncService redisSyncService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String userToken;
    private String adminToken;
    private Product testProduct;

    @AfterEach
    public void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @BeforeEach
    public void setUp() {
        // Clear all DB records
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // Clear all Redis keys
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        // 1. Seed standard products and users
        RegisterRequest userReg = RegisterRequest.builder()
                .username("wife_tester")
                .password("mypassword")
                .email("wife@test.com")
                .role(Role.ROLE_USER)
                .initialBalance(new BigDecimal("1000.00"))
                .build();
        restTemplate.postForEntity("/api/auth/register", userReg, User.class);

        RegisterRequest adminReg = RegisterRequest.builder()
                .username("admin_tester")
                .password("adminpassword")
                .email("admin@test.com")
                .role(Role.ROLE_ADMIN)
                .initialBalance(BigDecimal.ZERO)
                .build();
        restTemplate.postForEntity("/api/auth/register", adminReg, User.class);

        // 2. Fetch JWT tokens
        LoginRequest userLogin = LoginRequest.builder()
                .username("wife_tester")
                .password("mypassword")
                .build();
        ResponseEntity<JwtResponse> userLoginRes = restTemplate.postForEntity("/api/auth/login", userLogin, JwtResponse.class);
        assertEquals(HttpStatus.OK, userLoginRes.getStatusCode());
        userToken = userLoginRes.getBody().getToken();

        LoginRequest adminLogin = LoginRequest.builder()
                .username("admin_tester")
                .password("adminpassword")
                .build();
        ResponseEntity<JwtResponse> adminLoginRes = restTemplate.postForEntity("/api/auth/login", adminLogin, JwtResponse.class);
        assertEquals(HttpStatus.OK, adminLoginRes.getStatusCode());
        adminToken = adminLoginRes.getBody().getToken();

        // 3. Create a test product
        ProductDto productDto = ProductDto.builder()
                .name("MacBook Pro")
                .description("M3 Chip")
                .price(new BigDecimal("2000.00"))
                .stock(10)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<ProductDto> request = new HttpEntity<>(productDto, headers);

        ResponseEntity<Product> createRes = restTemplate.postForEntity("/api/products", request, Product.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        testProduct = createRes.getBody();

        // 4. Force warm-up cold synchronization to transfer data to Redis
        redisSyncService.onApplicationEvent(null);
    }

    @Test
    public void testProductAccessControl_UserCannotCreateProduct() {
        ProductDto productDto = ProductDto.builder()
                .name("iPhone 16")
                .price(new BigDecimal("1200.00"))
                .stock(5)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<ProductDto> request = new HttpEntity<>(productDto, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/products", request, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGetUserProfile() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                "/api/users/profile",
                HttpMethod.GET,
                entity,
                UserProfileResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileResponse profile = response.getBody();
        assertNotNull(profile);
        assertEquals("wife_tester", profile.getUsername());
        assertEquals("ROLE_USER", profile.getRole());
        assertEquals(0, new BigDecimal("1000.00").compareTo(profile.getBalance()));
    }

    @Test
    public void testSynchronousOrdering_Success() {
        OrderRequest orderRequest = OrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(1)
                        .build()))
                .build();

        // User initial balance is 1000.00, product price is 2000.00 -> Insufficient balance!
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<OrderRequest> request = new HttpEntity<>(orderRequest, headers);

        ResponseEntity<String> errRes = restTemplate.postForEntity("/api/orders", request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, errRes.getStatusCode());
        assertTrue(errRes.getBody().contains("Insufficient balance"));

        // Increase user balance in DB and sync to Redis
        User user = userRepository.findByUsername("wife_tester").orElseThrow();
        user.setBalance(new BigDecimal("3000.00"));
        userRepository.save(user);
        redisSyncService.onApplicationEvent(null);

        // Try placing order again (should succeed)
        ResponseEntity<OrderResponse> successRes = restTemplate.postForEntity("/api/orders", request, OrderResponse.class);
        assertEquals(HttpStatus.CREATED, successRes.getStatusCode());
        OrderResponse orderResponse = successRes.getBody();
        assertNotNull(orderResponse);
        assertEquals("PAID", orderResponse.getStatus());
        assertEquals(0, new BigDecimal("2000.00").compareTo(orderResponse.getTotalAmount()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(orderResponse.getRemainingBalance()));

        // Verify product stock in DB
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(9, updatedProduct.getStock());
    }

    @Test
    public void testAsynchronousOrdering_Success() throws InterruptedException {
        // Increase user balance in DB and sync to Redis
        User user = userRepository.findByUsername("wife_tester").orElseThrow();
        user.setBalance(new BigDecimal("5000.00"));
        userRepository.save(user);
        redisSyncService.onApplicationEvent(null);

        OrderRequest orderRequest = OrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(2) // Total cost 4000.00
                        .build()))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<OrderRequest> request = new HttpEntity<>(orderRequest, headers);

        // Place async order
        ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/api/orders/async", request, OrderResponse.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        OrderResponse orderResponse = response.getBody();
        assertNotNull(orderResponse);
        assertEquals("PENDING", orderResponse.getStatus());
        assertEquals(0, new BigDecimal("4000.00").compareTo(orderResponse.getTotalAmount()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(orderResponse.getRemainingBalance()));

        // Wait up to 5 seconds for RabbitMQ consumer to commit to database
        boolean processed = false;
        for (int i = 0; i < 25; i++) {
            Thread.sleep(200);
            List<Order> orders = orderRepository.findAll();
            if (!orders.isEmpty()) {
                Order order = orders.getFirst();
                if (order.getStatus() == OrderStatus.PAID) {
                    processed = true;
                    break;
                }
            }
        }

        assertTrue(processed, "The asynchronous order should be processed and written to PostgreSQL");

        // Verify DB state
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(8, updatedProduct.getStock());

        User updatedUser = userRepository.findByUsername("wife_tester").orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(updatedUser.getBalance()));
    }
}
