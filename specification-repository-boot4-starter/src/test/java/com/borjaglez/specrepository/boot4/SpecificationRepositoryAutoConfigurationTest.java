package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecificationRepositoryAutoConfigurationTest {

  @Test
  void shouldInstantiateAutoConfiguration() {
    assertThat(new SpecificationRepositoryAutoConfiguration()).isNotNull();
  }
}
