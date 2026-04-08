package com.borjaglez.specrepository.jpa.spi;

import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

@FunctionalInterface
public interface SpecificationRepositoryCustomizer {
  void customize(SpecificationRepositoryConfiguration.Builder builder);
}
