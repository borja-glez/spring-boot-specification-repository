package com.borjaglez.specrepository.jpa.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import com.borjaglez.specrepository.testsupport.PostgreSqlContainerSupport;

/**
 * Points a test context at the PostgreSQL container shared by every PostgreSQL test class of the
 * JVM (singleton container pattern). The container starts lazily, on the first context that needs
 * it, and Testcontainers' Ryuk removes it when the JVM exits. The {@code unaccent} extension, used
 * by {@code ignoreCase} filters, is created once right after start-up.
 *
 * <p>Every PostgreSQL class must use the same initializer and {@link
 * JpaIntegrationTestConfiguration} so that the Spring context is cached and reused as well.
 */
final class PostgresTestContainer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    PostgreSQLContainer<?> postgres = Holder.CONTAINER;
    TestPropertyValues.of(
            "spring.datasource.url=" + postgres.getJdbcUrl(),
            "spring.datasource.username=" + postgres.getUsername(),
            "spring.datasource.password=" + postgres.getPassword(),
            "spring.jpa.hibernate.ddl-auto=create-drop")
        .applyTo(context.getEnvironment());
  }

  private static final class Holder {
    private static final PostgreSQLContainer<?> CONTAINER = start();

    private static PostgreSQLContainer<?> start() {
      PostgreSQLContainer<?> postgres = PostgreSqlContainerSupport.create();
      postgres.start();
      try (Connection connection =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          Statement statement = connection.createStatement()) {
        statement.execute("create extension if not exists unaccent");
      } catch (SQLException e) {
        throw new IllegalStateException("Could not create the unaccent extension", e);
      }
      return postgres;
    }
  }
}
