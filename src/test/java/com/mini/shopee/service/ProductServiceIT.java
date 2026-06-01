package com.mini.shopee.service;

import com.mini.shopee.BaseIntegrationTest;
import com.mini.shopee.dto.ProductDto;
import com.mini.shopee.entity.Product;
import com.mini.shopee.exception.ResourceNotFoundException;
import com.mini.shopee.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductServiceIT extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        productRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    public void testCreateProduct_Success() {
        ProductDto dto = ProductDto.builder()
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("99.99"))
                .stock(50)
                .build();

        Product created = productService.createProduct(dto);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Test Product", created.getName());
        assertEquals("Test Description", created.getDescription());
        assertEquals(0, new BigDecimal("99.99").compareTo(created.getPrice()));
        assertEquals(50, created.getStock());

        // Verify it was saved in PostgreSQL
        Product dbProduct = productRepository.findById(created.getId()).orElse(null);
        assertNotNull(dbProduct);

        // Verify it was automatically synced to Redis
        String stockVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + created.getId());
        String priceVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + created.getId());
        String nameVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + created.getId());

        assertEquals("50", stockVal);
        assertEquals("99.99", priceVal);
        assertEquals("Test Product", nameVal);
    }

    @Test
    public void testUpdateProduct_Success() {
        ProductDto createDto = ProductDto.builder()
                .name("Original Name")
                .description("Original Desc")
                .price(new BigDecimal("10.00"))
                .stock(100)
                .build();
        Product product = productService.createProduct(createDto);

        ProductDto updateDto = ProductDto.builder()
                .name("Updated Name")
                .description("Updated Desc")
                .price(new BigDecimal("15.50"))
                .stock(80)
                .build();

        Product updated = productService.updateProduct(product.getId(), updateDto);

        assertNotNull(updated);
        assertEquals(product.getId(), updated.getId());
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated Desc", updated.getDescription());
        assertEquals(0, new BigDecimal("15.50").compareTo(updated.getPrice()));
        assertEquals(80, updated.getStock());

        // Verify Postgres has updated state
        Product dbProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Updated Name", dbProduct.getName());

        // Verify Redis cache has updated state
        String stockVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + product.getId());
        String priceVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + product.getId());
        String nameVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + product.getId());

        assertEquals("80", stockVal);
        assertEquals("15.50", priceVal);
        assertEquals("Updated Name", nameVal);
    }

    @Test
    public void testDeleteProduct_Success() {
        ProductDto createDto = ProductDto.builder()
                .name("To Be Deleted")
                .price(new BigDecimal("5.00"))
                .stock(10)
                .build();
        Product product = productService.createProduct(createDto);
        Long id = product.getId();

        // Delete product
        productService.deleteProduct(id);

        // Verify it was removed from PostgreSQL
        assertFalse(productRepository.existsById(id));

        // Verify it was evicted from Redis cache
        String stockVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_STOCK_PREFIX + id);
        String priceVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_PRICE_PREFIX + id);
        String nameVal = redisTemplate.opsForValue().get(ProductCacheService.REDIS_NAME_PREFIX + id);

        assertNull(stockVal);
        assertNull(priceVal);
        assertNull(nameVal);
    }

    @Test
    public void testGetProductById_ThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> {
            productService.getProductById(9999L);
        });
    }

    @Test
    public void testGetAllProducts() {
        ProductDto p1 = ProductDto.builder().name("Samsung Phone").price(new BigDecimal("100.00")).stock(5).build();
        ProductDto p2 = ProductDto.builder().name("Apple iPhone").price(new BigDecimal("200.00")).stock(10).build();
        productService.createProduct(p1);
        productService.createProduct(p2);

        Page<Product> all = productService.getAllProducts(null, PageRequest.of(0, 10));
        assertEquals(2, all.getTotalElements());

        Page<Product> filtered = productService.getAllProducts("iphone", PageRequest.of(0, 10));
        assertEquals(1, filtered.getTotalElements());
        assertEquals("Apple iPhone", filtered.getContent().getFirst().getName());
    }
}
