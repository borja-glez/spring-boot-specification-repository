package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

@SpringBootTest(classes = SpecificationRepositoryPostgresContainerTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class SpecificationRepositoryPostgresContainerTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @org.springframework.beans.factory.annotation.Autowired private TestCustomerRepository repository;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Test
  void shouldRunAgainstPostgres() {
    repository.save(new TestCustomer("Borja", "ACTIVE", new TestProfile("Madrid")));

    assertThat(repository.query().where("profile.city", Operators.EQUALS, "Madrid").count())
        .isEqualTo(1);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EnableJpaRepositories(
      basePackageClasses = TestCustomerRepository.class,
      repositoryBaseClass = SpecificationRepositoryImpl.class)
  @EntityScan(basePackageClasses = TestCustomer.class)
  static class TestConfiguration {}
}
