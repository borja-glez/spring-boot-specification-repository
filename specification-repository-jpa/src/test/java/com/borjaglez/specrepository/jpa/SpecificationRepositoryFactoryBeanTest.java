package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.repository.Repository;
import org.springframework.test.util.ReflectionTestUtils;

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

    assertThat(configuration.operatorHandlers()).hasSize(17);
    assertThat(configuration.valueConverters()).hasSize(3);
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

    Object resolvedConfiguration = ReflectionTestUtils.invokeMethod(factoryBean, "resolveConfiguration");

    assertThat(resolvedConfiguration).isSameAs(configuration);
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

  interface TestRepository extends Repository<Object, Long> {}
}
