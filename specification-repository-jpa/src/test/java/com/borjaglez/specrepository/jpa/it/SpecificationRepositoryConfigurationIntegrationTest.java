package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.SpecificationRepositoryCustomizer;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

@DataJpaTest
@ContextConfiguration(
    classes = SpecificationRepositoryConfigurationIntegrationTest.TestConfiguration.class)
class SpecificationRepositoryConfigurationIntegrationTest {
  private static final FilterOperator CUSTOM_EQUALS = Operators.custom("custom_equals");

  @Autowired private TestCustomerRepository repository;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    repository.save(
        new TestCustomer(
            "Borja", "ACTIVE", 25, java.time.LocalDate.of(2024, 1, 10), new TestProfile("Madrid")));
    repository.save(
        new TestCustomer(
            "Lucia",
            "ACTIVE",
            32,
            java.time.LocalDate.of(2024, 2, 15),
            new TestProfile("Barcelona")));
  }

  @Test
  void shouldApplyCustomOperatorHandlerFromConfigurationBean() {
    List<TestCustomer> results =
        repository.query().where("status", CUSTOM_EQUALS, "ACTIVE").findAll();

    assertThat(results)
        .extracting(TestCustomer::getName)
        .containsExactlyInAnyOrder("Borja", "Lucia");
  }

  @Test
  void shouldApplyCustomValueConverterFromConfigurationBean() {
    List<TestCustomer> results =
        repository.query().where("age", Operators.EQUALS, "age:25").findAll();

    assertThat(results).extracting(TestCustomer::getName).containsExactly("Borja");
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = TestCustomer.class)
  @EnableSpecificationRepositories(basePackages = "com.borjaglez.specrepository.jpa.it")
  static class TestConfiguration {
    @Bean
    SpecificationRepositoryConfiguration specificationRepositoryConfiguration(
        SpecificationRepositoryCustomizer customizer) {
      SpecificationRepositoryConfiguration.Builder builder =
          SpecificationRepositoryConfiguration.builder()
              .addDefaultOperatorHandlers()
              .addDefaultValueConverters();
      customizer.customize(builder);
      return builder.build();
    }

    @Bean
    SpecificationRepositoryCustomizer specificationRepositoryCustomizer() {
      return builder ->
          builder.addOperatorHandler(customEqualsOperatorHandler()).addValueConverter(ageValueConverter());
    }

    private static OperatorHandler customEqualsOperatorHandler() {
      return new OperatorHandler() {
        @Override
        public FilterOperator operator() {
          return CUSTOM_EQUALS;
        }

        @Override
        public Predicate create(OperatorContext context) {
          return context.criteriaBuilder().equal(context.path(), context.value());
        }
      };
    }

    private static ValueConverter ageValueConverter() {
      return new ValueConverter() {
        @Override
        public boolean supports(Class<?> targetType, FilterOperator operator) {
          return Integer.class.isAssignableFrom(targetType);
        }

        @Override
        public Object convert(Object value, Class<?> targetType, FilterOperator operator) {
          if (value instanceof String stringValue && stringValue.startsWith("age:")) {
            return Integer.valueOf(stringValue.substring(4));
          }
          return value;
        }
      };
    }
  }
}
