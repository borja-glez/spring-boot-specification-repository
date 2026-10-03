package com.borjaglez.specrepository.jpa.it;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

/**
 * Spring configuration shared by every JPA integration suite, on H2 and on PostgreSQL. Using the
 * same class everywhere keeps the Spring test context cache effective.
 *
 * <p>It records the generated SQL through {@link SqlStatementRecorder} and, on H2, registers an
 * {@code unaccent} function so that {@code ignoreCase} filters can run there too.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
    basePackageClasses = TestCustomerRepository.class,
    repositoryBaseClass = SpecificationRepositoryImpl.class)
@EntityScan(basePackageClasses = TestCustomer.class)
class JpaIntegrationTestConfiguration {

  @Bean
  SqlStatementRecorder sqlStatementRecorder() {
    return new SqlStatementRecorder();
  }

  @Bean
  HibernatePropertiesCustomizer sqlStatementRecorderCustomizer(SqlStatementRecorder recorder) {
    return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, recorder);
  }

  @Bean
  InitializingBean h2UnaccentFunction(DataSource dataSource) {
    return () -> {
      try (Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {
        if ("H2".equals(connection.getMetaData().getDatabaseProductName())) {
          statement.execute(
              "create alias if not exists unaccent for \""
                  + H2Functions.class.getName()
                  + ".unaccent\"");
        }
      } catch (SQLException e) {
        throw new IllegalStateException("Could not register the H2 unaccent function", e);
      }
    };
  }
}
