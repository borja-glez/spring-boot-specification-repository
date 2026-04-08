package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.specrepository.jpa.support.DefaultOperatorHandlers;
import com.borjaglez.specrepository.jpa.support.DefaultValueConverters;
import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

class SpecificationRepositoryFactoryBeanTest {

  @Test
  void shouldRequireListableBeanFactory() {
    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> factoryBean.setBeanFactory(mock(BeanFactory.class)))
        .withMessageContaining("beanFactory must be listable");
  }

  @Test
  void shouldUseDefaultConfigurationWhenNoConfigurationBeanExists() {
    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);
    factoryBean.setBeanFactory(new StaticListableBeanFactory());

    SpecificationRepositoryConfiguration configuration =
        (SpecificationRepositoryConfiguration)
            ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration");

    assertThat(configuration.operatorHandlers()).hasSize(DefaultOperatorHandlers.defaults().size());
    assertThat(configuration.valueConverters()).hasSize(DefaultValueConverters.defaults().size());
  }

  @Test
  void shouldUseDefaultConfigurationWhenBeanFactoryWasNotSet() {
    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);

    SpecificationRepositoryConfiguration configuration =
        (SpecificationRepositoryConfiguration)
            ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration");

    assertThat(configuration.operatorHandlers()).hasSize(DefaultOperatorHandlers.defaults().size());
    assertThat(configuration.valueConverters()).hasSize(DefaultValueConverters.defaults().size());
  }

  @Test
  void shouldUseSingleConfigurationBeanWhenPresent() {
    SpecificationRepositoryConfiguration configuration =
        SpecificationRepositoryConfiguration.builder()
            .addDefaultOperatorHandlers()
            .addDefaultValueConverters()
            .build();
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("specificationRepositoryConfiguration", configuration);

    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);
    factoryBean.setBeanFactory(beanFactory);

    Object resolvedConfiguration =
        ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration");

    assertThat(resolvedConfiguration).isSameAs(configuration);
  }

  @Test
  void shouldResolveLazyConfigurationBean() {
    GenericApplicationContext context = new GenericApplicationContext();
    context.registerBean(
        "specificationRepositoryConfiguration",
        SpecificationRepositoryConfiguration.class,
        SpecificationRepositoryConfiguration::defaultConfiguration,
        definition -> definition.setLazyInit(true));
    context.refresh();

    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);
    factoryBean.setBeanFactory(context);

    Object resolvedConfiguration =
        ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration");

    assertThat(resolvedConfiguration)
        .isSameAs(context.getBean("specificationRepositoryConfiguration"));
  }

  @Test
  void shouldRejectMultipleConfigurationBeans() {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean(
        "first",
        SpecificationRepositoryConfiguration.builder()
            .addDefaultOperatorHandlers()
            .addDefaultValueConverters()
            .build());
    beanFactory.addBean(
        "second",
        SpecificationRepositoryConfiguration.builder()
            .addDefaultOperatorHandlers()
            .addDefaultValueConverters()
            .build());

    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);
    factoryBean.setBeanFactory(beanFactory);

    assertThatIllegalStateException()
        .isThrownBy(() -> ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration"))
        .withMessageContaining("Expected a single SpecificationRepositoryConfiguration bean");
  }

  @Test
  void shouldUseSpecificationRepositoryImplAsRepositoryBaseClass() {
    SpecificationRepositoryFactoryBean<TestRepository, Object, Long> factoryBean =
        new SpecificationRepositoryFactoryBean<>(TestRepository.class);

    Object repositoryFactory =
        ReflectionTestUtils.invokeMethod(
            factoryBean, "createRepositoryFactory", mock(EntityManager.class, RETURNS_DEEP_STUBS));

    Object repositoryBaseClass =
        ReflectionTestUtils.invokeMethod(
            repositoryFactory, "getRepositoryBaseClass", mock(RepositoryMetadata.class));

    assertThat(repositoryBaseClass).isEqualTo(SpecificationRepositoryImpl.class);
  }

  interface TestRepository extends Repository<Object, Long> {}
}
