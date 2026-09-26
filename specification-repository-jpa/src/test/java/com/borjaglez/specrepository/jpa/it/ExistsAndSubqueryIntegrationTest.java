package com.borjaglez.specrepository.jpa.it;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

/** Runs the shared exists and subquery suite on the embedded H2 database. */
@DataJpaTest
@ContextConfiguration(classes = JpaIntegrationTestConfiguration.class)
class ExistsAndSubqueryIntegrationTest extends AbstractExistsAndSubqueryIntegrationTest {}
