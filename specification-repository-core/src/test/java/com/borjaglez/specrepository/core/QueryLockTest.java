package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class QueryLockTest {

  @Test
  void aNewPlanShouldNotLock() {
    QueryPlan<String> plan = SpecificationQueryBuilder.forEntity(String.class).build();

    assertThat(plan.lock()).isEqualTo(QueryLock.NONE);
    assertThat(plan.lock().isLocked()).isFalse();
    assertThat(plan.lock().mode()).isEqualTo(LockMode.NONE);
    assertThat(plan.lock().lockWait()).isEqualTo(LockWait.WAIT);
  }

  @Test
  void lockShouldWaitByDefault() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class).lock(LockMode.PESSIMISTIC_WRITE).build();

    assertThat(plan.lock()).isEqualTo(new QueryLock(LockMode.PESSIMISTIC_WRITE, LockWait.WAIT));
    assertThat(plan.lock().isLocked()).isTrue();
  }

  @Test
  void lockShouldStoreTheModeAndTheWait() {
    QueryPlan<String> plan =
        SpecificationQueryBuilder.forEntity(String.class)
            .lock(LockMode.PESSIMISTIC_READ, LockWait.NOWAIT)
            .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
            .build();

    assertThat(plan.lock())
        .isEqualTo(new QueryLock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED));
  }

  @Test
  void lockNoneShouldRemoveTheLock() {
    QueryPlan<String> locked =
        SpecificationQueryBuilder.forEntity(String.class)
            .lock(LockMode.PESSIMISTIC_WRITE, LockWait.SKIP_LOCKED)
            .build();

    QueryPlan<String> unlocked = locked.toBuilder().lock(LockMode.NONE).build();

    assertThat(unlocked.lock()).isEqualTo(QueryLock.NONE);
    assertThat(locked.lock().isLocked()).isTrue();
  }

  @Test
  void aDerivedPlanShouldKeepTheLock() {
    QueryPlan<String> locked =
        SpecificationQueryBuilder.forEntity(String.class)
            .lock(LockMode.PESSIMISTIC_WRITE, LockWait.NOWAIT)
            .build();

    QueryPlan<String> derived =
        locked.toBuilder().where("status", Operators.EQUALS, "NEW").sort(Sort.by("id")).build();

    assertThat(derived.lock()).isEqualTo(locked.lock());
  }

  @Test
  void shouldRejectAWaitWithoutALockMode() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new QueryLock(LockMode.NONE, LockWait.SKIP_LOCKED))
        .withMessage("A lock wait other than WAIT requires a lock mode other than NONE");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SpecificationQueryBuilder.forEntity(String.class)
                    .lock(LockMode.NONE, LockWait.NOWAIT))
        .withMessage("A lock wait other than WAIT requires a lock mode other than NONE");
  }

  @Test
  void shouldRejectNullLockParts() {
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryLock(null, LockWait.WAIT))
        .withMessage("mode must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> new QueryLock(LockMode.PESSIMISTIC_WRITE, null))
        .withMessage("lockWait must not be null");
  }

  @Test
  void queryPlanShouldRejectANullLock() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new QueryPlan<>(
                    String.class,
                    new GroupCondition(LogicalOperator.AND, List.of()),
                    new GroupCondition(LogicalOperator.AND, List.of()),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    Sort.unsorted(),
                    false,
                    AllowedFieldsPolicy.allowAll(),
                    null))
        .withMessage("lock must not be null");
  }

  @Test
  void constructorWithoutLockShouldNotLock() {
    QueryPlan<String> plan =
        new QueryPlan<>(
            String.class,
            new GroupCondition(LogicalOperator.AND, List.of()),
            new GroupCondition(LogicalOperator.AND, List.of()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            Sort.unsorted(),
            false,
            AllowedFieldsPolicy.allowAll());

    assertThat(plan.lock()).isEqualTo(QueryLock.NONE);
  }
}
