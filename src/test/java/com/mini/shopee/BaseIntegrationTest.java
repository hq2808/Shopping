package com.mini.shopee;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;
    static final GenericContainer<?> redis;
    static final GenericContainer<?> rabbitmq;

    static {
        String testProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if ("test-docker".equalsIgnoreCase(testProfile)) {
            postgres = null;
            redis = null;
            rabbitmq = null;
        } else {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shopee_test")
                    .withUsername("postgres")
                    .withPassword("password");
            postgres.start();

            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redis.start();

            rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:3-alpine"))
                    .withExposedPorts(5672);
            rabbitmq.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
        if (redis != null) {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }
        if (rabbitmq != null) {
            registry.add("spring.rabbitmq.host", rabbitmq::getHost);
            registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        }
    }
}
