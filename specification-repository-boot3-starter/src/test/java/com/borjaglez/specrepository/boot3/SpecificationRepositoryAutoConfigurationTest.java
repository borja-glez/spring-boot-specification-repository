package com.borjaglez.specrepository.boot3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryFactoryBean;
import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;
import com.borjaglez.specrepository.jpa.spi.OperatorHandler;
import com.borjaglez.specrepository.jpa.spi.SpecificationRepositoryCustomizer;
import com.borjaglez.specrepository.jpa.spi.ValueConverter;
import com.borjaglez.specrepository.jpa.support.PathResolver;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

class SpecificationRepositoryAutoConfigurationTest {

  @Test
  void shouldInstantiateAutoConfiguration() {
    assertThat(new SpecificationRepositoryAutoConfiguration()).isNotNull();
  }

  @Test
  void shouldImportSpecificationJpaRepositoriesRegistrar() {
    Import importAnnotation =
        SpecificationRepositoryAutoConfiguration.class.getAnnotation(Import.class);

    assertThat(importAnnotation).isNotNull();
    assertThat(importAnnotation.value())
        .containsExactly(SpecificationJpaRepositoriesRegistrar.class);
  }

  @Test
  void shouldConfigureJpaRepositoriesToUseSpecificationRepositoryBaseClass() {
    SpecificationJpaRepositoriesRegistrar registrar = new SpecificationJpaRepositoriesRegistrar();

    assertThat(invoke(registrar, "getAnnotation")).isEqualTo(EnableJpaRepositories.class);

    Class<?> configurationType = invoke(registrar, "getConfiguration", Class.class);
    EnableJpaRepositories annotation = configurationType.getAnnotation(EnableJpaRepositories.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.repositoryBaseClass()).isEqualTo(SpecificationRepositoryImpl.class);
    assertThat(annotation.repositoryFactoryBeanClass())
        .isEqualTo(SpecificationRepositoryFactoryBean.class);
  }

  @Test
  void shouldExposeExtensionPointsThroughConfigurationBean() {
    GenericApplicationContext context = new GenericApplicationContext();
    context.registerBean(
        "customOperatorHandler", OperatorHandler.class, this::customOperatorHandler);
    context.registerBean("customValueConverter", ValueConverter.class, this::customValueConverter);
    context.registerBean("customPathResolver", PathResolver.class, PathResolver::new);
    context.registerBean(
        "specificationRepositoryCustomizer",
        SpecificationRepositoryCustomizer.class,
        () ->
            builder ->
                builder
                    .pathResolver(context.getBean("customPathResolver", PathResolver.class))
                    .clearValueConverters()
                    .addValueConverter(
                        context.getBean("customValueConverter", ValueConverter.class)));
    context.refresh();

    SpecificationRepositoryConfiguration configuration =
        new SpecificationRepositoryAutoConfiguration()
            .specificationRepositoryConfiguration(
                context.getBeanProvider(OperatorHandler.class),
                context.getBeanProvider(ValueConverter.class),
                context.getBeanProvider(SpecificationRepositoryCustomizer.class),
                context.getBeanProvider(org.springframework.core.convert.ConversionService.class),
                context.getBeanProvider(PathResolver.class),
                context.getBeanProvider(
                    com.borjaglez.specrepository.jpa.support.QueryPlanSpecificationFactory.class));

    assertThat(configuration.pathResolver())
        .isSameAs(context.getBean("customPathResolver", PathResolver.class));
    assertThat(configuration.valueConverters())
        .containsExactly(context.getBean("customValueConverter", ValueConverter.class));
    assertThat(configuration.operatorHandlers())
        .anyMatch(handler -> handler.operator().equals(Operators.custom("jsonb_eq")));
  }

  @Test
  void shouldCreateJpaRepositoryConfigurationExtension() {
    SpecificationJpaRepositoriesRegistrar registrar = new SpecificationJpaRepositoriesRegistrar();

    assertThat(invoke(registrar, "getRepositoryConfigurationExtension"))
        .isInstanceOf(JpaRepositoryConfigExtension.class);
  }

  @Test
  void shouldUseDefaultBootstrapModeWhenPropertyIsMissing() {
    SpecificationJpaRepositoriesRegistrar registrar = new SpecificationJpaRepositoriesRegistrar();

    ReflectionTestUtils.invokeMethod(registrar, "setEnvironment", new MockEnvironment());

    assertThat(invoke(registrar, "getBootstrapMode")).isEqualTo(BootstrapMode.DEFAULT);
  }

  @Test
  void shouldResolveBootstrapModeFromEnvironmentIgnoringCase() {
    SpecificationJpaRepositoriesRegistrar registrar = new SpecificationJpaRepositoriesRegistrar();

    ReflectionTestUtils.invokeMethod(
        registrar,
        "setEnvironment",
        new MockEnvironment()
            .withProperty("spring.data.jpa.repositories.bootstrap-mode", "deferred"));

    assertThat(invoke(registrar, "getBootstrapMode")).isEqualTo(BootstrapMode.DEFERRED);
  }

  private static Object invoke(SpecificationJpaRepositoriesRegistrar registrar, String methodName) {
    return ReflectionTestUtils.invokeMethod(registrar, methodName);
  }

  private static <T> T invoke(
      SpecificationJpaRepositoriesRegistrar registrar, String methodName, Class<T> resultType) {
    return resultType.cast(ReflectionTestUtils.invokeMethod(registrar, methodName));
  }

  private OperatorHandler customOperatorHandler() {
    OperatorHandler operatorHandler = org.mockito.Mockito.mock(OperatorHandler.class);
    when(operatorHandler.operator()).thenReturn(Operators.custom("jsonb_eq"));
    return operatorHandler;
  }

  private ValueConverter customValueConverter() {
    return new ValueConverter() {
      @Override
      public boolean supports(
          Class<?> targetType, com.borjaglez.specrepository.core.FilterOperator operator) {
        return true;
      }

      @Override
      public Object convert(
          Object value,
          Class<?> targetType,
          com.borjaglez.specrepository.core.FilterOperator operator) {
        return value;
      }
    };
  }
}
