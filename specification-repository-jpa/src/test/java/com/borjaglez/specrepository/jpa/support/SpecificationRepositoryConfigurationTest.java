package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;

import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.spi.OperatorContext;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;

class SpecificationRepositoryConfigurationTest {

  @Test
  void shouldCreateDefaultConfigurationWithDefaultExtensions() {
    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.defaultConfiguration();

    assertThat(configuration.operatorHandlers()).hasSize(DefaultOperatorHandlers.defaults().size());
    assertThat(configuration.valueConverters()).hasSize(DefaultValueConverters.defaults().size());
    assertThat(configuration.pathResolver()).isNotNull();
    assertThat(configuration.specificationFactory()).isNotNull();
  }

  @Test
  void shouldReplaceConfiguredExtensionsWithCollections() {
    OperatorHandler operatorHandler = customOperatorHandler();
    ValueConverter valueConverter = customValueConverter();

    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.builder()
            .addDefaultOperatorHandlers()
            .addDefaultValueConverters()
            .operatorHandlers(List.of(operatorHandler))
            .valueConverters(List.of(valueConverter))
            .build();

    assertThat(configuration.operatorHandlers()).containsExactly(operatorHandler);
    assertThat(configuration.valueConverters()).containsExactly(valueConverter);
  }

  @Test
  void shouldApplyExplicitConversionService() {
    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.builder()
            .conversionService(new DefaultFormattingConversionService())
            .addDefaultOperatorHandlers()
            .addDefaultValueConverters()
            .build();

    assertThat(configuration.specificationFactory()).isNotNull();
  }

  @Test
  void shouldRejectNullCollaborators() {
    assertThatNullPointerException()
        .isThrownBy(() -> SpecificationRepositoryConfiguration.builder().addOperatorHandler(null))
        .withMessage("operatorHandler must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> SpecificationRepositoryConfiguration.builder().addValueConverter(null))
        .withMessage("valueConverter must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> SpecificationRepositoryConfiguration.builder().conversionService(null))
        .withMessage("conversionService must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> SpecificationRepositoryConfiguration.builder().pathResolver(null))
        .withMessage("pathResolver must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> SpecificationRepositoryConfiguration.builder().specificationFactory(null))
        .withMessage("specificationFactory must not be null");
  }

  @Test
  void shouldAllowMatchingExplicitPathResolverWithFactory() {
    PathResolver pathResolver = new PathResolver();
    QueryPlanSpecificationFactory specificationFactory =
        new QueryPlanSpecificationFactory(
            new OperatorRegistry(DefaultOperatorHandlers.defaults()),
            new ValueConversionService(
                new DefaultFormattingConversionService(), DefaultValueConverters.defaults()),
            pathResolver);

    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.builder()
            .pathResolver(pathResolver)
            .specificationFactory(specificationFactory)
            .build();

    assertThat(configuration.pathResolver()).isSameAs(pathResolver);
    assertThat(configuration.specificationFactory()).isSameAs(specificationFactory);
  }

  @Test
  void shouldReuseSpecificationFactoryPathResolverWhenFactoryIsProvided() {
    PathResolver pathResolver = new PathResolver();
    QueryPlanSpecificationFactory specificationFactory =
        new QueryPlanSpecificationFactory(
            new OperatorRegistry(DefaultOperatorHandlers.defaults()),
            new ValueConversionService(
                new DefaultFormattingConversionService(), DefaultValueConverters.defaults()),
            pathResolver);

    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.builder()
            .specificationFactory(specificationFactory)
            .build();

    assertThat(configuration.pathResolver()).isSameAs(pathResolver);
    assertThat(configuration.specificationFactory()).isSameAs(specificationFactory);
  }

  @Test
  void shouldRejectDifferentPathResolverWhenFactoryIsProvided() {
    QueryPlanSpecificationFactory specificationFactory =
        new QueryPlanSpecificationFactory(
            new OperatorRegistry(DefaultOperatorHandlers.defaults()),
            new ValueConversionService(
                new DefaultFormattingConversionService(), DefaultValueConverters.defaults()),
            new PathResolver());

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SpecificationRepositoryConfiguration.builder()
                    .pathResolver(new PathResolver())
                    .specificationFactory(specificationFactory)
                    .build())
        .withMessage("pathResolver must match specificationFactory.pathResolver");
  }

  @Test
  void shouldExposeSpecificationFactoryCollaborators() {
    OperatorRegistry operatorRegistry = new OperatorRegistry(DefaultOperatorHandlers.defaults());
    ValueConversionService valueConversionService =
        new ValueConversionService(
            new DefaultFormattingConversionService(), DefaultValueConverters.defaults());
    PathResolver pathResolver = new PathResolver();

    QueryPlanSpecificationFactory specificationFactory =
        new QueryPlanSpecificationFactory(operatorRegistry, valueConversionService, pathResolver);

    assertThat(specificationFactory.operatorRegistry()).isSameAs(operatorRegistry);
    assertThat(specificationFactory.valueConversionService()).isSameAs(valueConversionService);
    assertThat(specificationFactory.pathResolver()).isSameAs(pathResolver);
  }

  private static OperatorHandler customOperatorHandler() {
    return new OperatorHandler() {
      @Override
      public FilterOperator operator() {
        return Operators.custom("custom");
      }

      @Override
      public Predicate create(OperatorContext context) {
        return null;
      }
    };
  }

  private static ValueConverter customValueConverter() {
    return new ValueConverter() {
      @Override
      public boolean supports(Class<?> targetType, FilterOperator operator) {
        return true;
      }

      @Override
      public Object convert(Object value, Class<?> targetType, FilterOperator operator) {
        return value;
      }
    };
  }
}
