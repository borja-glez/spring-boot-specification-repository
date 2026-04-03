package com.borjaglez.specrepository.examples.boot3postgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DOCKER_HOST", matches = ".*", disabledReason = "Requires Docker")
class Boot3PostgresDemoApplicationTest {
  @Test
  void contextLoads() {}
}
