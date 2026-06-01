package com.mini.shopee.service;

import com.mini.shopee.config.RabbitMQConfig;
import com.mini.shopee.dto.*;
import com.mini.shopee.entity.*;
import com.mini.shopee.exception.BadRequestException;
import com.mini.shopee.exception.ResourceNotFoundException;
import com.mini.shopee.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ProductCacheService productCacheService;

    private DefaultRedisScript<Long> getStockReserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new org.springframework.core.io.ClassPathResource("scripts/stock_reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    public OrderResponse createOrderAsync(String username, OrderRequest request) {
        // 1. Fetch User (ensure exists)
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        // 2. Validate request
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order items list cannot be empty");
        }
        if (request.getItems().size() > 50) {
            throw new BadRequestException("Order contains too many items. Maximum allowed is 50 items per checkout.");
        }

        // 3. Batch fetch all product metadata from Redis (Zero PostgreSQL queries if cached, automatic lazy Read-Through population otherwise!)
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .collect(Collectors.toList());

        ProductCacheService.ProductMetadataBatch metadataBatch = productCacheService.getProductMetadata(productIds);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<String> keys = new ArrayList<>();
        // KEYS[1]: User balance key
        keys.add(RedisSyncService.REDIS_BALANCE_PREFIX + username);

        List<String> args = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        // Calculate total cost and map KEYS / ARGV in-memory
        for (OrderItemRequest itemReq : request.getItems()) {
            Long productId = itemReq.getProductId();
            String name = metadataBatch.getNames().get(productId);
            BigDecimal price = metadataBatch.getPrices().get(productId);

            if (name == null || price == null) {
                throw new ResourceNotFoundException("One or more products in your cart do not exist.");
            }

            // KEYS[2...n]: Product stock keys
            keys.add(RedisSyncService.REDIS_STOCK_PREFIX + productId);
            // ARGV[1...n-1]: Product quantities requested
            args.add(String.valueOf(itemReq.getQuantity()));

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            itemResponses.add(OrderItemResponse.builder()
                    .productId(productId)
                    .productName(name)
                    .quantity(itemReq.getQuantity())
                    .price(price)
                    .subtotal(subtotal)
                    .build());
        }

        // ARGV[n]: Total price as the last argument
        args.add(totalAmount.toString());

        Long result = redisTemplate.execute(
                getStockReserveScript(),
                keys,
                args.toArray()
        );

        if (result == null) {
            throw new BadRequestException("Failed to process order reservation.");
        }

        if (result == -1) {
            throw new BadRequestException("One or more products in your order are out of stock.");
        }

        if (result == -2) {
            throw new BadRequestException("Insufficient wallet balance to place this order.");
        }

        // 4. Result is 1: Pre-reservation success! Publish Order trace to RabbitMQ
        String traceId = UUID.randomUUID().toString();
        OrderMessage orderMessage = OrderMessage.builder()
                .traceId(traceId)
                .username(username)
                .items(request.getItems())
                .totalAmount(totalAmount)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                orderMessage
        );

        // 5. Calculate remaining balance locally
        BigDecimal remainingBalance = user.getBalance().subtract(totalAmount);

        // Return immediate response with PENDING status
        return OrderResponse.builder()
                .orderId(null) // Generated asynchronously later
                .username(username)
                .totalAmount(totalAmount)
                .status("PENDING")
                .createdAt(java.time.LocalDateTime.now())
                .items(itemResponses)
                .remainingBalance(remainingBalance)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(String username, OrderRequest request) {
        // 1. Fetch current User
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        // 2. Validate request items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order items list cannot be empty");
        }
        if (request.getItems().size() > 50) {
            throw new BadRequestException("Order contains too many items. Maximum allowed is 50 items per checkout.");
        }

        // 3. Batch fetch all products in ONE database roundtrip
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new ResourceNotFoundException("One or more products in your cart do not exist.");
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        // 4. Process each item in-memory (check stock, deduct stock, calculate price)
        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());

            if (product.getStock() < itemReq.getQuantity()) {
                throw new BadRequestException("Product '" + product.getName() + "' is out of stock. Available stock: " + product.getStock() + ", requested: " + itemReq.getQuantity());
            }

            // Deduct stock in-memory
            product.setStock(product.getStock() - itemReq.getQuantity());

            // Calculate cost
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            // Construct OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build();
            orderItems.add(orderItem);

            // Construct Response item
            itemResponses.add(OrderItemResponse.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .subtotal(subtotal)
                    .build());
        }

        // Save all products updated in batch
        productRepository.saveAll(products);

        // 5. Verify wallet balance
        if (user.getBalance().compareTo(totalAmount) < 0) {
            throw new BadRequestException("Insufficient balance. Order total: " + totalAmount + ", your balance: " + user.getBalance());
        }

        // Deduct balance
        user.setBalance(user.getBalance().subtract(totalAmount));
        userRepository.save(user);

        // 6. Create Order
        Order order = Order.builder()
                .user(user)
                .totalAmount(totalAmount)
                .status(OrderStatus.PAID)
                .items(new ArrayList<>())
                .build();

        // Bidirectional linking
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
            order.getItems().add(orderItem);
        }

        // 7. Save Order (cascades save to orderItems)
        Order savedOrder = orderRepository.save(order);

        // 8. Return detailed response
        return OrderResponse.builder()
                .orderId(savedOrder.getId())
                .username(user.getUsername())
                .totalAmount(savedOrder.getTotalAmount())
                .status(savedOrder.getStatus().name())
                .createdAt(savedOrder.getCreatedAt())
                .items(itemResponses)
                .remainingBalance(user.getBalance())
                .build();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        List<Order> orders = orderRepository.findByUserWithItemsAndProducts(user);
        
        return orders.stream().map(order -> {
            List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> 
                OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build()
            ).collect(Collectors.toList());

            return OrderResponse.builder()
                    .orderId(order.getId())
                    .username(user.getUsername())
                    .totalAmount(order.getTotalAmount())
                    .status(order.getStatus().name())
                    .createdAt(order.getCreatedAt())
                    .items(itemResponses)
                    .remainingBalance(user.getBalance())
                    .build();
        }).collect(Collectors.toList());
    }
}
