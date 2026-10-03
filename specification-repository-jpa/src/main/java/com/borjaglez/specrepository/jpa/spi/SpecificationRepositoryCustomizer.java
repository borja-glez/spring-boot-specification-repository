package com.borjaglez.specrepository.jpa.spi;

import static org.apiguardian.api.API.Status.MAINTAINED;

import org.apiguardian.api.API;

import com.borjaglez.specrepository.jpa.support.SpecificationRepositoryConfiguration;

@FunctionalInterface
@API(status = MAINTAINED, since = "1.0.0")
public interface SpecificationRepositoryCustomizer {
  void customize(SpecificationRepositoryConfiguration.Builder builder);
}
