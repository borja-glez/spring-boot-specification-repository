package com.borjaglez.specrepository.jpa.it;

/**
 * Runs the shared specification repository suite on PostgreSQL 17. Tests that fail only on
 * PostgreSQL because of a library defect are overridden here with {@code @Disabled("PostgreSQL:
 * <reason>")}.
 */
@PostgresIntegrationTest
class SpecificationRepositoryPostgresIntegrationTest
    extends AbstractSpecificationRepositoryIntegrationTest {}
