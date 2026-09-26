package com.borjaglez.specrepository.jpa.it;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs a JPA integration suite on the shared PostgreSQL 17 container instead of H2. Classes
 * annotated with it share one container and one Spring context, and are skipped when Docker is not
 * available.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ContextConfiguration(
    classes = JpaIntegrationTestConfiguration.class,
    initializers = PostgresTestContainer.class)
@Testcontainers(disabledWithoutDocker = true)
@interface PostgresIntegrationTest {}
