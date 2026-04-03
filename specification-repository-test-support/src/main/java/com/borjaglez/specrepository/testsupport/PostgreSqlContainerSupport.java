package com.borjaglez.specrepository.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgreSqlContainerSupport {
  private PostgreSqlContainerSupport() {}

  public static PostgreSQLContainer<?> create() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }
}
