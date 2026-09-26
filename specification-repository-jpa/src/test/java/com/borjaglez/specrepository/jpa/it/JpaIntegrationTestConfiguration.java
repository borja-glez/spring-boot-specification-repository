package com.borjaglez.specrepository.jpa.it;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.borjaglez.specrepository.jpa.SpecificationRepositoryImpl;

/**
 * Spring configuration shared by every JPA integration suite, on H2 and on PostgreSQL. Using the
 * same class everywhere keeps the Spring test context cache effective.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
    basePackageClasses = TestCustomerRepository.class,
    repositoryBaseClass = SpecificationRepositoryImpl.class)
@EntityScan(basePackageClasses = TestCustomer.class)
class JpaIntegrationTestConfiguration {}
