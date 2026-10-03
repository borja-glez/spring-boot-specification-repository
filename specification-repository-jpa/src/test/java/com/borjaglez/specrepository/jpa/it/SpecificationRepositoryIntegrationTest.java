package com.borjaglez.specrepository.jpa.it;

import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Runs the shared specification repository suite on the embedded H2 database. Tests that cannot run
 * on H2 are overridden here with {@link Disabled} and the reason. The {@code unaccent} function
 * used by {@code ignoreCase} is registered on H2 by {@link JpaIntegrationTestConfiguration}.
 */
@DataJpaTest
@ContextConfiguration(classes = JpaIntegrationTestConfiguration.class)
class SpecificationRepositoryIntegrationTest
    extends AbstractSpecificationRepositoryIntegrationTest {}
