package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.borjaglez.specrepository.jpa.EnableSpecificationRepositories;

/** On Spring Framework 7, as Boot 4 applications run it: the packages must reach Spring Data. */
class EnableSpecificationRepositoriesBasePackagesTest {

  @EnableSpecificationRepositories(basePackages = "com.acme.repositories")
  static class Configured {}

  @Test
  void basePackagesReachSpringData() {
    AnnotationAttributes attributes =
        AnnotatedElementUtils.getMergedAnnotationAttributes(
            Configured.class, EnableJpaRepositories.class);

    assertThat(attributes).isNotNull();
    assertThat(attributes.getStringArray("basePackages")).containsExactly("com.acme.repositories");
  }
}
