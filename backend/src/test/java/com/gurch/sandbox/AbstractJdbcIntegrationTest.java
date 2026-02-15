package com.gurch.sandbox;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public abstract class AbstractJdbcIntegrationTest {
  // Shared Spring Boot + Testcontainers context for integration tests.
}
