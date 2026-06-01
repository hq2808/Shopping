package com.mini.shopee.service;

import com.mini.shopee.BaseIntegrationTest;
import com.mini.shopee.entity.Product;
import com.mini.shopee.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductCacheServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Product product1;
    private Product product2;

    @BeforeEach
    public void setUp() {
        productRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        product1 = Product.builder()
                .name("iPhone 15")
                .description("Base model")
                .price(new BigDecimal("999.00"))
                .stock(10)
                .build();

        product2 = Product.builder()
                .name("iPad Pro")
                .description("M4 model")
                .price(new BigDecimal("1199.00"))
                .stock(5)
                .build();

        productRepository.saveAll(List.of(product1, product2));
    }

    @AfterEach
    public void cleanUp() {
        productRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    public void testSyncProduct_Success() {
        productCacheService.syncProduct(product1);

        String stockVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + product1.getId());
        String priceVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + product1.getId());
        String nameVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + product1.getId());

        assertEquals("10", stockVal);
        assertEquals("999.00", priceVal);
        assertEquals("iPhone 15", nameVal);
    }

    @Test
    public void testEvictProduct_Success() {
        productCacheService.syncProduct(product1);
        productCacheService.evictProduct(product1.getId());

        String stockVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + product1.getId());
        String priceVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + product1.getId());
        String nameVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + product1.getId());

        assertNull(stockVal);
        assertNull(priceVal);
        assertNull(nameVal);
    }

    @Test
    public void testGetProductMetadata_CacheMiss_ReadThroughAndLazyPopulate() {
        // Initially, the Redis cache is empty (Cache Miss!)
        List<Long> ids = List.of(product1.getId(), product2.getId());

        // Perform batch get metadata (should trigger SQL fetch and lazy Redis sync)
        ProductCacheService.ProductMetadataBatch batch = productCacheService.getProductMetadata(ids);

        assertNotNull(batch);
        assertEquals(2, batch.getNames().size());
        assertEquals(2, batch.getPrices().size());

        assertEquals("iPhone 15", batch.getNames().get(product1.getId()));
        assertEquals(0, new BigDecimal("999.00").compareTo(batch.getPrices().get(product1.getId())));

        assertEquals("iPad Pro", batch.getNames().get(product2.getId()));
        assertEquals(0, new BigDecimal("1199.00").compareTo(batch.getPrices().get(product2.getId())));

        // Verify that the cache was populated lazily
        String stockVal1 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + product1.getId());
        String priceVal1 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + product1.getId());
        String nameVal1 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + product1.getId());

        assertEquals("10", stockVal1);
        assertEquals("999.00", priceVal1);
        assertEquals("iPhone 15", nameVal1);

        String stockVal2 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + product2.getId());
        String priceVal2 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + product2.getId());
        String nameVal2 = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + product2.getId());

        assertEquals("5", stockVal2);
        assertEquals("1199.00", priceVal2);
        assertEquals("iPad Pro", nameVal2);
    }

    @Test
    public void testGetProductMetadata_CacheHit_NoSQLRead() {
        // Pre-populate cache (Cache Hit!)
        productCacheService.syncProduct(product1);
        productCacheService.syncProduct(product2);

        // Modify the DB records to verify we do NOT fetch from SQL (proving true In-Memory isolation)
        product1.setName("Database Tampered iPhone 15");
        product1.setPrice(new BigDecimal("10.00"));
        productRepository.save(product1);

        // Fetch metadata
        ProductCacheService.ProductMetadataBatch batch = productCacheService.getProductMetadata(
                List.of(product1.getId(), product2.getId())
        );

        assertNotNull(batch);
        // Verify it returned the CACHED name/price, not the database-tampered values
        assertEquals("iPhone 15", batch.getNames().get(product1.getId()));
        assertEquals(0, new BigDecimal("999.00").compareTo(batch.getPrices().get(product1.getId())));
    }
}
