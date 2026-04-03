package com.borjaglez.specrepository.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableJpaRepositories(repositoryBaseClass = SpecificationRepositoryImpl.class)
public @interface EnableSpecificationRepositories {
  String[] basePackages() default {};
}
