package com.borjaglez.specrepository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class EnableSpecificationRepositoriesTest {

  @Test
  void shouldUseSpecificationRepositoryFactoryBean() {
    EnableJpaRepositories annotation =
        EnableSpecificationRepositories.class.getAnnotation(EnableJpaRepositories.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.repositoryBaseClass()).isEqualTo(SpecificationRepositoryImpl.class);
    assertThat(annotation.repositoryFactoryBeanClass())
        .isEqualTo(SpecificationRepositoryFactoryBean.class);
  }
}
