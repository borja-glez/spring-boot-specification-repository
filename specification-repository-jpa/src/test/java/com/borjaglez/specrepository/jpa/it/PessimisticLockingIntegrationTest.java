package com.borjaglez.specrepository.jpa.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Runs the pessimistic locking suite on the embedded H2 database. Hibernate's H2 dialect renders
 * every lock as a plain {@code FOR UPDATE}, without {@code NOWAIT} or {@code SKIP LOCKED}, and a
 * read lock as a write lock: the tests that rely on those are overridden here with {@link Disabled}
 * and covered by the PostgreSQL run.
 */
@DataJpaTest
@ContextConfiguration(classes = JpaIntegrationTestConfiguration.class)
class PessimisticLockingIntegrationTest extends AbstractPessimisticLockingIntegrationTest {

  private static final String NO_SKIP_LOCKED = "H2 dialect: no SKIP LOCKED";
  private static final String NO_NOWAIT = "H2 dialect: no NOWAIT";
  private static final String NO_READ_LOCK = "H2 dialect: a read lock is a FOR UPDATE";

  @Override
  @Test
  @Disabled(NO_SKIP_LOCKED)
  void skipLockedShouldHandTheNextBatchToASecondTransaction() {}

  @Override
  @Test
  @Disabled(NO_SKIP_LOCKED)
  void skipLockedShouldWorkWithEveryEntityTerminal() {}

  @Override
  @Test
  @Disabled(NO_SKIP_LOCKED)
  void aPlanDerivedFromALockedPlanShouldKeepTheLock() {}

  @Override
  @Test
  @Disabled(NO_NOWAIT)
  void noWaitShouldFailAtOnceOnALockedRow() {}

  @Override
  @Test
  @Disabled(NO_READ_LOCK)
  void aReadLockShouldShareTheRowsButBlockAWriteLock() {}
}
