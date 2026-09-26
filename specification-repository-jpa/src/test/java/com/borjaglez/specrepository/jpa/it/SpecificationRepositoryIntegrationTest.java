package com.borjaglez.specrepository.jpa.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Runs the shared specification repository suite on the embedded H2 database. Tests that cannot run
 * on H2 are overridden here with {@link Disabled} and the reason.
 */
@DataJpaTest
@ContextConfiguration(classes = JpaIntegrationTestConfiguration.class)
class SpecificationRepositoryIntegrationTest
    extends AbstractSpecificationRepositoryIntegrationTest {

  private static final String NO_UNACCENT =
      "H2: no unaccent function; ignoreCase is covered by the PostgreSQL run";

  @Override
  @Test
  @Disabled(NO_UNACCENT)
  void ignoreCaseShouldAlsoIgnoreAccentsInTheSearchTerm() {}

  @Override
  @Test
  @Disabled(NO_UNACCENT)
  void ignoreCaseShouldMatchLikeWildcardsInTheSearchTermLiterally() {}
}
