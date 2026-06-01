package com.mini.shopee.service;

import com.mini.shopee.BaseIntegrationTest;
import com.mini.shopee.dto.OrderItemRequest;
import com.mini.shopee.dto.OrderMessage;
import com.mini.shopee.entity.*;
import com.mini.shopee.repository.OrderRepository;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class OrderConsumerIT extends BaseIntegrationTest {

    @Autowired
    private OrderConsumer orderConsumer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    public void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .username("consumer_tester")
                .password("pass")
                .email("consumer@test.com")
                .role(Role.ROLE_USER)
                .balance(new BigDecimal("100.00"))
                .build();
        userRepository.save(testUser);

        testProduct = Product.builder()
                .name("Consumer Product")
                .description("Desc")
                .price(new BigDecimal("20.00"))
                .stock(10)
                .build();
        productRepository.save(testProduct);
    }

    @AfterEach
    public void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testReceiveOrder_Success() {
        String traceId = UUID.randomUUID().toString();
        OrderMessage message = OrderMessage.builder()
                .traceId(traceId)
                .username("consumer_tester")
                .items(List.of(OrderItemRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(2)
                        .build()))
                .totalAmount(new BigDecimal("40.00"))
                .build();

        // Trigger consumer directly
        orderConsumer.receiveOrder(message);

        // Verify user balance deducted in database
        User dbUser = userRepository.findByUsername("consumer_tester").orElseThrow();
        assertEquals(0, new BigDecimal("60.00").compareTo(dbUser.getBalance()));

        // Verify product stock deducted in database
        Product dbProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(8, dbProduct.getStock());

        // Verify order saved in database
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order order = orders.getFirst();
        assertEquals(traceId, order.getTraceId());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(order.getTotalAmount()));

        // Verify order items saved
        assertEquals(1, order.getItems().size());
        OrderItem item = order.getItems().getFirst();
        assertEquals(testProduct.getId(), item.getProduct().getId());
        assertEquals(2, item.getQuantity());
        assertEquals(0, new BigDecimal("20.00").compareTo(item.getPrice()));
    }

    @Test
    public void testReceiveOrder_Idempotency_DuplicateMessageSkipped() {
        String traceId = UUID.randomUUID().toString();
        OrderMessage message = OrderMessage.builder()
                .traceId(traceId)
                .username("consumer_tester")
                .items(List.of(OrderItemRequest.builder()
                        .productId(testProduct.getId())
                        .quantity(1)
                        .build()))
                .totalAmount(new BigDecimal("20.00"))
                .build();

        // Trigger consumer once (should succeed)
        orderConsumer.receiveOrder(message);

        // Trigger consumer second time with exact same traceId (should skip processing)
        orderConsumer.receiveOrder(message);

        // Verify database balance was only deducted ONCE (should be 80.00, not 60.00)
        User dbUser = userRepository.findByUsername("consumer_tester").orElseThrow();
        assertEquals(0, new BigDecimal("80.00").compareTo(dbUser.getBalance()));

        // Verify database stock was only deducted ONCE (should be 9, not 8)
        Product dbProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(9, dbProduct.getStock());

        // Verify only 1 order exists in database
        assertEquals(1, orderRepository.findAll().size());
    }
}
