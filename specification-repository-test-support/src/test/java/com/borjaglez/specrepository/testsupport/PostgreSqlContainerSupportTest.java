package com.borjaglez.specrepository.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgreSqlContainerSupportTest {

  @Test
  void shouldCreateContainerDefinition() {
    assertThat(PostgreSqlContainerSupport.create()).isNotNull();
  }
}
