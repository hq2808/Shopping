package com.mini.shopee.controller;

import com.mini.shopee.dto.OrderRequest;
import com.mini.shopee.dto.OrderResponse;
import com.mini.shopee.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request, Principal principal) {
        OrderResponse response = orderService.createOrder(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/async")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> createOrderAsync(@Valid @RequestBody OrderRequest request, Principal principal) {
        OrderResponse response = orderService.createOrderAsync(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getUserOrders(Principal principal) {
        List<OrderResponse> responses = orderService.getUserOrders(principal.getName());
        return ResponseEntity.ok(responses);
    }
}
