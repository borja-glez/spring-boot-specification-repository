package com.borjaglez.specrepository.jpa.spi;

import jakarta.persistence.criteria.Predicate;

import com.borjaglez.specrepository.core.FilterOperator;

public interface OperatorHandler {
  FilterOperator operator();

  Predicate create(OperatorContext context);
}
