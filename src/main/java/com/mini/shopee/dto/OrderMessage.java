package com.mini.shopee.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMessage {
    private String traceId;
    private String username;
    private List<OrderItemRequest> items;
    private BigDecimal totalAmount;
}
