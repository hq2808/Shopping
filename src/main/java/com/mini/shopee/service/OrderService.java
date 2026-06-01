package com.mini.shopee.service;

import com.mini.shopee.dto.*;
import com.mini.shopee.entity.*;
import com.mini.shopee.exception.BadRequestException;
import com.mini.shopee.exception.ResourceNotFoundException;
import com.mini.shopee.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(String username, OrderRequest request) {
        // 1. Fetch current User
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        // 2. Validate request items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order items list cannot be empty");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        // 3. Process each item (check stock, deduct stock, calculate price)
        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));

            if (product.getStock() < itemReq.getQuantity()) {
                throw new BadRequestException("Product '" + product.getName() + "' is out of stock. Available stock: " + product.getStock() + ", requested: " + itemReq.getQuantity());
            }

            // Deduct stock
            product.setStock(product.getStock() - itemReq.getQuantity());
            productRepository.save(product);

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

        // 4. Verify wallet balance
        if (user.getBalance().compareTo(totalAmount) < 0) {
            throw new BadRequestException("Insufficient balance. Order total: " + totalAmount + ", your balance: " + user.getBalance());
        }

        // Deduct balance
        user.setBalance(user.getBalance().subtract(totalAmount));
        userRepository.save(user);

        // 5. Create Order
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

        // 6. Save Order (cascades save to orderItems)
        Order savedOrder = orderRepository.save(order);

        // 7. Return detailed response
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
