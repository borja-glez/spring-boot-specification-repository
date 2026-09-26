package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

@SpringBootTest(classes = SpecificationRepositoryPostgresContainerTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class SpecificationRepositoryPostgresContainerTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @org.springframework.beans.factory.annotation.Autowired private TestCustomerRepository repository;

  @org.springframework.beans.factory.annotation.Autowired private JdbcTemplate jdbcTemplate;

  @org.springframework.beans.factory.annotation.Autowired
  private TestOrderRepository orderRepository;

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

  @Test
  void ignoreCaseShouldAlsoIgnoreAccentsInTheSearchTerm() {
    // Requires the unaccent extension, as documented for case-insensitive search on PostgreSQL.
    jdbcTemplate.execute("create extension if not exists unaccent");
    repository.save(new TestCustomer("Café Ramón", "ACTIVE", null));
    repository.save(new TestCustomer("CAFE MOLIDO", "ACTIVE", null));
    repository.save(new TestCustomer("Té verde", "ACTIVE", null));

    assertThat(repository.query().where("name", Operators.CONTAINS, "café", true, false).count())
        .isEqualTo(2);
    assertThat(repository.query().where("name", Operators.EQUALS, "te verde", true, false).count())
        .isEqualTo(1);
  }

  @Test
  void shouldGroupByToManyAssociationPathOnPostgres() {
    TestCustomer borja = repository.save(new TestCustomer("Borja", "ACTIVE", null));
    TestCustomer lucia = repository.save(new TestCustomer("Lucia", "ACTIVE", null));
    orderRepository.save(new TestOrder(new BigDecimal("10.00"), "PAID", false, borja));
    orderRepository.save(new TestOrder(new BigDecimal("20.00"), "PENDING", false, borja));
    orderRepository.save(new TestOrder(new BigDecimal("30.00"), "PAID", true, lucia));

    // PostgreSQL rejects the query when the selection and the GROUP BY use different joins.
    List<GroupedRow> rows =
        repository
            .query()
            .where("orders.status", Operators.IS_NOT_NULL, null)
            .groupBy("orders.status")
            .sort(Sort.by("orders.status"))
            .select("orders.status")
            .countAs("customers", "id")
            .findAllGrouped();

    assertThat(rows).extracting(row -> row.get("orders.status")).containsExactly("PAID", "PENDING");
    assertThat(rows).extracting(row -> row.get("customers")).containsExactly(2L, 1L);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EnableJpaRepositories(
      basePackageClasses = TestCustomerRepository.class,
      repositoryBaseClass = SpecificationRepositoryImpl.class)
  @EntityScan(basePackageClasses = TestCustomer.class)
  static class TestConfiguration {}
}
