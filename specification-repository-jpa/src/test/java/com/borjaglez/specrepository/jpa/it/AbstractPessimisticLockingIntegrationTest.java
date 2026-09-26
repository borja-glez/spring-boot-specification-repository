package com.borjaglez.specrepository.jpa.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.TransactionRequiredException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.specrepository.core.LockMode;
import com.borjaglez.specrepository.core.LockWait;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Pessimistic row locks taken by a plan. Each test holds the lock in a first transaction and runs a
 * second, independent transaction ({@code REQUIRES_NEW}, on its own connection) while the first is
 * still open, so the tests prove what the database does, not only the SQL sent. The test methods
 * run without a test transaction: the rows are committed, and removed after each test.
 *
 * <p>Run on PostgreSQL by {@link PessimisticLockingPostgresIntegrationTest} and on H2 by {@link
 * PessimisticLockingIntegrationTest}.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractPessimisticLockingIntegrationTest {

  @Autowired private TestCustomerRepository customers;
  @Autowired private TestOrderRepository orders;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    inTransaction(
        () -> {
          orders.deleteAll();
          customers.deleteAll();
          for (String name : List.of("Anna", "Borja", "Carla", "David")) {
            TestCustomer customer =
                new TestCustomer(name, "QUEUED", 30, LocalDate.of(2024, 1, 1), null);
            customer.addOrder(new TestOrder(BigDecimal.TEN, "PAID", false, customer));
            customers.save(customer);
          }
          customers.save(new TestCustomer("Elena", "SENT", 30, LocalDate.of(2024, 1, 1), null));
          return null;
        });
  }

  @AfterEach
  void tearDown() {
    inTransaction(
        () -> {
          orders.deleteAll();
          customers.deleteAll();
          return null;
        });
  }

  private SpecificationExecutableQuery<TestCustomer> queued() {
    return customers.query().where("status", Operators.EQUALS, "QUEUED").sort(Sort.by("name"));
  }

  @Test
  void skipLockedShouldHandTheNextBatchToASecondTransaction() {
    List<List<String>> batches =
        inTransaction(
            () -> {
              Page<TestCustomer> first =
                  queued()
                      .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
                      .findAll(PageRequest.of(0, 2));
              Page<TestCustomer> second =
                  inNewTransaction(
                      () ->
                          queued()
                              .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
                              .findAll(PageRequest.of(0, 2)));
              // The count query of a page is not locked: it still counts the skipped rows.
              assertThat(second.getTotalElements()).isEqualTo(4);
              return List.of(names(first.getContent()), names(second.getContent()));
            });

    assertThat(batches).containsExactly(List.of("Anna", "Borja"), List.of("Carla", "David"));
  }

  @Test
  void skipLockedShouldWorkWithEveryEntityTerminal() {
    inTransaction(
        () -> {
          customers
              .query()
              .where("name", Operators.EQUALS, "Anna")
              .lock(LockMode.PESSIMISTIC_WRITE)
              .findAll();
          inNewTransaction(
              () -> {
                assertThat(names(skipLockedQueued().findAll()))
                    .containsExactly("Borja", "Carla", "David");
                Slice<TestCustomer> slice = skipLockedQueued().findSlice(PageRequest.of(0, 2));
                assertThat(names(slice.getContent())).containsExactly("Borja", "Carla");
                assertThat(slice.hasNext()).isTrue();
                assertThat(skipLockedQueued().findOne())
                    .map(TestCustomer::getName)
                    .contains("Borja");
                assertThat(names(customers.findAll(skipLockedQueued().plan())))
                    .containsExactly("Borja", "Carla", "David");
                return null;
              });
          return null;
        });
  }

  private SpecificationExecutableQuery<TestCustomer> skipLockedQueued() {
    return queued().lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED);
  }

  @Test
  void aPlanDerivedFromALockedPlanShouldKeepTheLock() {
    QueryPlan<TestCustomer> locked =
        SpecificationQueryBuilder.forEntity(TestCustomer.class)
            .sort(Sort.by("name"))
            .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
            .build();

    List<String> seen =
        inTransaction(
            () -> {
              customers.query(locked).where("name", Operators.EQUALS, "Anna").findAll();
              return inNewTransaction(
                  () ->
                      names(
                          customers
                              .query(locked)
                              .where("status", Operators.EQUALS, "QUEUED")
                              .findAll()));
            });

    assertThat(seen).containsExactly("Borja", "Carla", "David");
  }

  @Test
  void noWaitShouldFailAtOnceOnALockedRow() {
    inTransaction(
        () -> {
          queued().lock(LockMode.PESSIMISTIC_WRITE).findAll();
          assertThatThrownBy(
                  () ->
                      inNewTransaction(
                          () ->
                              queued()
                                  .lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT)
                                  .findAll(PageRequest.of(0, 2))))
              .isInstanceOf(LockTimeoutException.class);
          // Through the repository proxy, the exception is translated.
          QueryPlan<TestCustomer> noWait =
              queued().lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT).plan();
          assertThatThrownBy(() -> inNewTransaction(() -> customers.findAll(noWait)))
              .isInstanceOf(PessimisticLockingFailureException.class);
          // Rows nobody holds are locked at once.
          assertThat(
                  inNewTransaction(
                      () ->
                          customers
                              .query()
                              .where("status", Operators.EQUALS, "SENT")
                              .lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT)
                              .findOne()))
              .map(TestCustomer::getName)
              .contains("Elena");
          return null;
        });
  }

  @Test
  void aReadLockShouldShareTheRowsButBlockAWriteLock() {
    inTransaction(
        () -> {
          queued().lock(LockMode.PESSIMISTIC_READ).findAll();
          assertThat(
                  inNewTransaction(
                      () ->
                          names(
                              queued().lock(LockMode.PESSIMISTIC_READ, LockWait.NOWAIT).findAll())))
              .containsExactly("Anna", "Borja", "Carla", "David");
          assertThatThrownBy(
                  () ->
                      inNewTransaction(
                          () ->
                              queued().lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT).findAll()))
              .isInstanceOf(LockTimeoutException.class);
          return null;
        });
  }

  @Test
  void aLockedQueryShouldFetchACollectionWhenItReadsEveryRow() {
    List<Integer> orderCounts =
        inTransaction(
            () ->
                queued().leftFetch("orders").lock(LockMode.PESSIMISTIC_WRITE).findAll().stream()
                    .map(customer -> customer.getOrders().size())
                    .toList());

    assertThat(orderCounts).containsExactly(1, 1, 1, 1);
  }

  @Test
  void shouldRejectALockWithACollectionFetchWhenPaging() {
    String message =
        "A locked query cannot fetch a collection when it reads a page or a single entity:"
            + " load the collection after locking the rows";
    inTransaction(
        () -> {
          assertThatIllegalStateException()
              .isThrownBy(
                  () ->
                      queued()
                          .leftFetch("orders")
                          .lock(LockMode.PESSIMISTIC_WRITE)
                          .findAll(PageRequest.of(0, 2)))
              .withMessage(message);
          assertThatIllegalStateException()
              .isThrownBy(
                  () -> queued().leftFetch("orders").lock(LockMode.PESSIMISTIC_WRITE).findOne())
              .withMessage(message);
          return null;
        });
  }

  @Test
  void shouldRejectALockWithSelections() {
    String message =
        "A lock applies to entity queries only: it cannot be combined with select, aggregates"
            + " or selectInto";
    inTransaction(
        () -> {
          assertThatIllegalStateException()
              .isThrownBy(() -> queued().select("name").lock(LockMode.PESSIMISTIC_WRITE).findRows())
              .withMessage(message);
          assertThatIllegalStateException()
              .isThrownBy(
                  () ->
                      queued()
                          .select("name")
                          .lock(LockMode.PESSIMISTIC_WRITE)
                          .selectInto(NameOnly.class)
                          .findAll(PageRequest.of(0, 2)))
              .withMessage(message);
          return null;
        });
  }

  @Test
  void shouldRejectALockWithGroupBy() {
    inTransaction(
        () -> {
          assertThatIllegalStateException()
              .isThrownBy(
                  () ->
                      customers
                          .query()
                          .groupBy("status")
                          .lock(LockMode.PESSIMISTIC_WRITE)
                          .findAll())
              .withMessage("A locked query cannot group rows: remove groupBy or the lock");
          return null;
        });
  }

  @Test
  void shouldRejectALockWithDistinct() {
    String message =
        "A locked query cannot be distinct, which databases such as PostgreSQL reject: remove"
            + " distinct(), or filter a collection with exists(...) instead of a path through it";
    inTransaction(
        () -> {
          assertThatIllegalStateException()
              .isThrownBy(() -> queued().distinct().lock(LockMode.PESSIMISTIC_WRITE).findAll())
              .withMessage(message);
          assertThatIllegalStateException()
              .isThrownBy(
                  () ->
                      queued()
                          .where("orders.status", Operators.EQUALS, "PAID")
                          .lock(LockMode.PESSIMISTIC_WRITE)
                          .findAll(PageRequest.of(0, 2)))
              .withMessage(message);
          return null;
        });
  }

  @Test
  void aLockedQueryShouldFilterACollectionWithExists() {
    List<String> names =
        inTransaction(
            () ->
                names(
                    queued()
                        .<TestOrder>exists(
                            "orders", sub -> sub.where("status", Operators.EQUALS, "PAID"))
                        .lock(LockMode.PESSIMISTIC_WRITE)
                        .findAll()));

    assertThat(names).containsExactly("Anna", "Borja", "Carla", "David");
  }

  @Test
  void theCountOfALockedPlanShouldNotLock() {
    long count =
        inTransaction(
            () -> {
              queued().lock(LockMode.PESSIMISTIC_WRITE).findAll();
              return inNewTransaction(
                  () -> queued().lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT).count());
            });

    assertThat(count).isEqualTo(4);
  }

  @Test
  void aLockedQueryShouldNeedATransaction() {
    assertThatThrownBy(() -> queued().lock(LockMode.PESSIMISTIC_WRITE).findAll())
        .isInstanceOf(TransactionRequiredException.class);
  }

  @Test
  void aLockedPlanShouldBeRejectedInTheReadOnlyTransactionOfTheRepository() {
    QueryPlan<TestCustomer> plan = queued().lock(LockMode.PESSIMISTIC_WRITE).plan();

    // Called through the repository proxy, which wraps the IllegalStateException.
    assertThatThrownBy(() -> customers.findAll(plan))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessage(
            "A locked query needs a read-write transaction, and keeps the lock until it ends:"
                + " run it inside a @Transactional method that is not read-only");
  }

  private static List<String> names(List<TestCustomer> customers) {
    return customers.stream().map(TestCustomer::getName).toList();
  }

  private <R> R inTransaction(Supplier<R> body) {
    return transaction(TransactionDefinition.PROPAGATION_REQUIRED).execute(status -> body.get());
  }

  private <R> R inNewTransaction(Supplier<R> body) {
    return transaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        .execute(status -> body.get());
  }

  private TransactionTemplate transaction(int propagation) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(propagation);
    return template;
  }

  record NameOnly(String name) {}
}
