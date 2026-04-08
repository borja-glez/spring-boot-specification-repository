package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;

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
            .pathResolver(new PathResolver())
            .specificationFactory(specificationFactory)
            .build();

    assertThat(configuration.pathResolver()).isSameAs(pathResolver);
    assertThat(configuration.specificationFactory()).isSameAs(specificationFactory);
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
}
