package com.example.nss.post.infrastructure.repository;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
abstract class AbstractPostgresSchemaIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine").withInitScript("nss-schema/app.sql");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.docker.compose.enabled", () -> false);
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:db/migration/V1__create_news.sql");
    registry.add("analytics.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("analytics.datasource.username", POSTGRES::getUsername);
    registry.add("analytics.datasource.password", POSTGRES::getPassword);
  }
}
