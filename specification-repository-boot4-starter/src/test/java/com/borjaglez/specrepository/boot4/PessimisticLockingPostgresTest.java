package com.borjaglez.specrepository.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.persistence.LockTimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.borjaglez.specrepository.core.LockMode;
import com.borjaglez.specrepository.core.LockWait;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Pessimistic row locks on Hibernate 7 (Spring Boot 4) and PostgreSQL: the lock timeout hint must
 * still render {@code SKIP LOCKED} and {@code NOWAIT}. The Hibernate 6 run is in the JPA module.
 */
@SpringBootTest(
    classes = ExtensionTestApplication.class,
    // create, not create-drop: the container stops before the cached context closes.
    properties = "spring.jpa.hibernate.ddl-auto=create")
@Testcontainers(disabledWithoutDocker = true)
class PessimisticLockingPostgresTest {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private ExtensionTestProductRepository products;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    products.deleteAll();
    for (String name : List.of("a", "b", "c", "d")) {
      products.save(new ExtensionTestProduct(name, UUID.randomUUID()));
    }
  }

  @AfterEach
  void tearDown() {
    products.deleteAll();
  }

  private SpecificationExecutableQuery<ExtensionTestProduct> byName() {
    return products.query().sort(Sort.by("name"));
  }

  @Test
  void skipLockedShouldHandTheNextBatchToASecondTransaction() {
    List<List<String>> batches =
        transaction(
            TransactionDefinition.PROPAGATION_REQUIRED,
            () -> {
              List<String> first =
                  names(
                      byName()
                          .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
                          .findAll(PageRequest.of(0, 2))
                          .getContent());
              List<String> second =
                  transaction(
                      TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                      () ->
                          names(
                              byName()
                                  .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
                                  .findAll(PageRequest.of(0, 2))
                                  .getContent()));
              return List.of(first, second);
            });

    assertThat(batches).containsExactly(List.of("a", "b"), List.of("c", "d"));
  }

  @Test
  void noWaitShouldFailAtOnceOnALockedRowAndAReadLockShouldShareIt() {
    transaction(
        TransactionDefinition.PROPAGATION_REQUIRED,
        () -> {
          byName().lock(LockMode.PESSIMISTIC_READ).findAll();
          assertThat(
                  transaction(
                      TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                      () -> byName().lock(LockMode.PESSIMISTIC_READ, LockWait.NOWAIT).findAll()))
              .hasSize(4);
          assertThatThrownBy(
                  () ->
                      transaction(
                          TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                          () ->
                              byName().lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT).findAll()))
              .isInstanceOf(LockTimeoutException.class);
          return null;
        });
  }

  private static List<String> names(List<ExtensionTestProduct> products) {
    return products.stream().map(ExtensionTestProduct::getName).toList();
  }

  private <R> R transaction(int propagation, Supplier<R> body) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(propagation);
    return template.execute(status -> body.get());
  }
}
