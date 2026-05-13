package com.naengo.api_server.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 베이스. Postgres+pgvector 컨테이너를 JVM 단위 singleton 으로 한 번만 부팅
 * (모든 테스트 클래스가 공유). 각 테스트 후 모든 테이블 TRUNCATE 로 격리.
 *
 * <p>{@code @Testcontainers + @Container} 패턴 대신 static block 에서 직접 start →
 * JUnit 의 perClass shutdown 이슈 회피 (테스트 클래스 간 컨테이너 재사용 보장).
 *
 * <p>Flyway 가 V1~V3 자동 적용 → 각 테스트는 빈 DB 에서 시작.
 *
 * <p>웹 환경: RANDOM_PORT + Spring 6 표준 RestClient. 4xx/5xx 도 그대로 받기 위해
 * default error handling 비활성화.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    @SuppressWarnings("resource") // JVM 종료 시 testcontainers 가 정리
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("naengo")
                    .withUsername("naengo")
                    .withPassword("naengo");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    protected RestClient client;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpClient() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(s -> true, (req, res) -> { /* 4xx/5xx 도 통과 */ })
                .build();
    }

    @AfterEach
    void truncateAll() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery("""
                    TRUNCATE TABLE
                      chat_messages, chat_rooms,
                      likes, scraps, recipe_stats,
                      pending_recipes, recipes,
                      user_profiles, users
                    RESTART IDENTITY CASCADE
                    """).executeUpdate());
    }
}
