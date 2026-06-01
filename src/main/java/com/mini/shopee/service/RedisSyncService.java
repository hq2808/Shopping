package com.mini.shopee.service;

import com.mini.shopee.entity.Product;
import com.mini.shopee.entity.User;
import com.mini.shopee.repository.ProductRepository;
import com.mini.shopee.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSyncService implements ApplicationListener<ApplicationReadyEvent> {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ProductCacheService productCacheService;

    public static final String REDIS_STOCK_PREFIX = "product:stock:";
    public static final String REDIS_BALANCE_PREFIX = "user:balance:";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Starting database-to-Redis cold sync warm-up...");
        try {
            // 1. Sync Product Stock, Price, and Name
            List<Product> products = productRepository.findAll();
            for (Product product : products) {
                productCacheService.syncProduct(product);
            }
            log.info("Successfully synced {} products stock, prices, and names to Redis", products.size());

            // 2. Sync User Balance
            List<User> users = userRepository.findAll();
            for (User user : users) {
                String key = REDIS_BALANCE_PREFIX + user.getUsername();
                redisTemplate.opsForValue().set(key, user.getBalance().toString());
            }
            log.info("Successfully synced {} users balance to Redis", users.size());

            log.info("Database-to-Redis warm-up completed successfully!");
        } catch (Exception e) {
            log.error("Failed to perform Redis startup warm-up: {}", e.getMessage(), e);
        }
    }
}
