package com.mini.shopee.service;

import com.mini.shopee.entity.Product;
import com.mini.shopee.exception.ResourceNotFoundException;
import com.mini.shopee.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCacheService {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    public static final String REDIS_STOCK_PREFIX = "product:stock:";
    public static final String REDIS_PRICE_PREFIX = "product:price:";
    public static final String REDIS_NAME_PREFIX = "product:name:";

    /**
     * Syncs a product's stock, price, and name to Redis (Write-Through)
     */
    public void syncProduct(Product product) {
        String stockKey = REDIS_STOCK_PREFIX + product.getId();
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));

        String priceKey = REDIS_PRICE_PREFIX + product.getId();
        redisTemplate.opsForValue().set(priceKey, product.getPrice().toString());

        String nameKey = REDIS_NAME_PREFIX + product.getId();
        redisTemplate.opsForValue().set(nameKey, product.getName());
    }

    /**
     * Evicts a product's keys from Redis (Cache Eviction)
     */
    public void evictProduct(Long productId) {
        String stockKey = REDIS_STOCK_PREFIX + productId;
        String priceKey = REDIS_PRICE_PREFIX + productId;
        String nameKey = REDIS_NAME_PREFIX + productId;
        redisTemplate.delete(List.of(stockKey, priceKey, nameKey));
    }

    /**
     * Batch retrieves product names and prices from Redis with automatic database Read-Through lazy population
     */
    public ProductMetadataBatch getProductMetadata(List<Long> productIds) {
        List<String> priceKeys = productIds.stream()
                .map(id -> REDIS_PRICE_PREFIX + id)
                .collect(Collectors.toList());
        List<String> nameKeys = productIds.stream()
                .map(id -> REDIS_NAME_PREFIX + id)
                .collect(Collectors.toList());

        List<String> priceValues = redisTemplate.opsForValue().multiGet(priceKeys);
        List<String> nameValues = redisTemplate.opsForValue().multiGet(nameKeys);

        Map<Long, BigDecimal> productPriceMap = new HashMap<>();
        Map<Long, String> productNameMap = new HashMap<>();
        List<Long> missingProductIds = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Long pId = productIds.get(i);
            String priceStr = priceValues != null && i < priceValues.size() ? priceValues.get(i) : null;
            String nameStr = nameValues != null && i < nameValues.size() ? nameValues.get(i) : null;

            if (priceStr == null || nameStr == null) {
                missingProductIds.add(pId);
            } else {
                productPriceMap.put(pId, new BigDecimal(priceStr));
                productNameMap.put(pId, nameStr);
            }
        }

        // Lazy Population (Read-Through Cache Miss handling)
        if (!missingProductIds.isEmpty()) {
            log.info("Cache miss detected in Redis for products: {}. Lazy populating from PostgreSQL...", missingProductIds);
            List<Product> dbProducts = productRepository.findAllById(missingProductIds);
            if (dbProducts.size() != missingProductIds.size()) {
                throw new ResourceNotFoundException("One or more products in your cart do not exist.");
            }

            for (Product product : dbProducts) {
                // Populate to Redis
                syncProduct(product);

                // Add to return maps
                productPriceMap.put(product.getId(), product.getPrice());
                productNameMap.put(product.getId(), product.getName());
            }
        }

        return new ProductMetadataBatch(productPriceMap, productNameMap);
    }

    @lombok.Value
    public static class ProductMetadataBatch {
        Map<Long, BigDecimal> prices;
        Map<Long, String> names;
    }
}
