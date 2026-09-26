package com.borjaglez.specrepository.jpa.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.borjaglez.specrepository.core.Operators;

class OperatorRegistryTest {

  @Test
  void shouldResolveDefaultOperator() {
    OperatorRegistry registry = new OperatorRegistry(DefaultOperatorHandlers.defaults());

    assertThat(registry.get(Operators.EQUALS)).isNotNull();
  }

  @Test
  void shouldFailForUnknownOperator() {
    OperatorRegistry registry = new OperatorRegistry(DefaultOperatorHandlers.defaults());

    assertThatThrownBy(() -> registry.get(Operators.custom("jsonb_eq")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("jsonb_eq");
  }

  @Test
  void findShouldReturnTheRegisteredHandler() {
    OperatorRegistry registry = new OperatorRegistry(DefaultOperatorHandlers.defaults());

    assertThat(registry.find(Operators.EQUALS)).containsSame(registry.get(Operators.EQUALS));
  }

  @Test
  void findShouldReturnEmptyForUnknownOperator() {
    OperatorRegistry registry = new OperatorRegistry(DefaultOperatorHandlers.defaults());

    assertThat(registry.find(Operators.custom("jsonb_eq"))).isEmpty();
  }
}
