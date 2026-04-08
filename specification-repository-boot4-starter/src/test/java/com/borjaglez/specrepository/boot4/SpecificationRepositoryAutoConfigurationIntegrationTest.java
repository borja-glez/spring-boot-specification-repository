package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

@SpringBootTest(
    classes = {
      ExtensionTestApplication.class,
      SpecificationRepositoryAutoConfigurationIntegrationTest.CustomizationConfiguration.class
    },
    properties = {
      "spring.datasource.url=jdbc:h2:mem:boot4-extension-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class SpecificationRepositoryAutoConfigurationIntegrationTest {
  private static final FilterOperator CUSTOM_EQUALS = Operators.custom("custom_equals");

  @Autowired private ExtensionTestProductRepository repository;

  private UUID externalId;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    externalId = UUID.randomUUID();
    repository.save(new ExtensionTestProduct("alpha", externalId));
    repository.save(new ExtensionTestProduct("beta", UUID.randomUUID()));
  }

  @Test
  void shouldApplyCustomOperatorHandlerBeans() {
    List<ExtensionTestProduct> results =
        repository.query().where("name", CUSTOM_EQUALS, "alpha").findAll();

    assertThat(results).extracting(ExtensionTestProduct::getName).containsExactly("alpha");
  }

  @Test
  void shouldApplyCustomValueConverterBeans() {
    List<ExtensionTestProduct> results =
        repository.query().where("externalId", Operators.EQUALS, externalId.toString()).findAll();

    assertThat(results).extracting(ExtensionTestProduct::getExternalId).containsExactly(externalId);
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomizationConfiguration {
    @Bean
    OperatorHandler customEqualsOperatorHandler() {
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

    @Bean
    ValueConverter uuidStringValueConverter() {
      return new ValueConverter() {
        @Override
        public boolean supports(Class<?> targetType, FilterOperator operator) {
          return UUID.class.isAssignableFrom(targetType);
        }

        @Override
        public Object convert(Object value, Class<?> targetType, FilterOperator operator) {
          return value instanceof String stringValue ? UUID.fromString(stringValue) : value;
        }
      };
    }
  }
}
