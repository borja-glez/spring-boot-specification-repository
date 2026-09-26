package com.borjaglez.specrepository.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableJpaRepositories(
    repositoryBaseClass = SpecificationRepositoryImpl.class,
    repositoryFactoryBeanClass = SpecificationRepositoryFactoryBean.class)
public @interface EnableSpecificationRepositories {

  /**
   * Packages to scan for repositories. Passed on to {@link EnableJpaRepositories#basePackages()};
   * without the alias Spring Data never saw it and scanned the package of the annotated class.
   */
  @AliasFor(annotation = EnableJpaRepositories.class)
  String[] basePackages() default {};
}
