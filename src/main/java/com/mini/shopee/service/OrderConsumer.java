package com.mini.shopee.service;

import com.mini.shopee.config.RabbitMQConfig;
import com.mini.shopee.dto.OrderItemRequest;
import com.mini.shopee.dto.OrderMessage;
import com.mini.shopee.entity.*;
import com.mini.shopee.repository.OrderRepository;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConsumer {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(OrderMessage message) {
        log.info("Consumer received order message with traceId: {}", message.getTraceId());

        try {
            // 1. Idempotency Check: Avoid duplicate delivery
            if (orderRepository.existsByTraceId(message.getTraceId())) {
                log.warn("Duplicate order traceId detected: {}. Skipping database write.", message.getTraceId());
                return;
            }

            // 2. Fetch User
            User user = userRepository.findByUsername(message.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found: " + message.getUsername()));

            // 3. Deduct DB Balance (already checked/reserved on Redis)
            user.setBalance(user.getBalance().subtract(message.getTotalAmount()));
            userRepository.save(user);

            // 4. Create Order
            Order order = Order.builder()
                    .traceId(message.getTraceId())
                    .user(user)
                    .totalAmount(message.getTotalAmount())
                    .status(OrderStatus.PAID)
                    .items(new ArrayList<>())
                    .build();

            // 5. Process products and link items
            for (OrderItemRequest itemReq : message.getItems()) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + itemReq.getProductId()));

                // Deduct DB stock
                product.setStock(product.getStock() - itemReq.getQuantity());
                productRepository.save(product);

                // Construct and link OrderItem
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .quantity(itemReq.getQuantity())
                        .price(product.getPrice())
                        .build();
                order.getItems().add(orderItem);
            }

            // 6. Save Order (cascades save to items)
            orderRepository.save(order);
            log.info("Successfully saved order to database for traceId: {}", message.getTraceId());

        } catch (Exception e) {
            log.error("Failed to process and save order message with traceId: {}. Reason: {}", message.getTraceId(), e.getMessage(), e);
            // In a production app, we would throw the exception to trigger RabbitMQ retry/DLQ mechanisms
            throw e;
        }
    }
}
