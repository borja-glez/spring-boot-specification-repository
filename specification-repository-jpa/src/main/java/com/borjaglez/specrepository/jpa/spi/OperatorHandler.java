package com.borjaglez.specrepository.jpa.spi;

import static org.apiguardian.api.API.Status.MAINTAINED;

import jakarta.persistence.criteria.Predicate;

import org.apiguardian.api.API;

import com.borjaglez.specrepository.core.FilterOperator;

@API(status = MAINTAINED, since = "1.0.0")
public interface OperatorHandler {
  FilterOperator operator();

  Predicate create(OperatorContext context);
}
