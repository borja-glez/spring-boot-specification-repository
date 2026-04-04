package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

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
}
