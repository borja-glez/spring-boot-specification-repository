package com.borjaglez.specrepository.jpa.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

public record OperatorContext(
    CriteriaBuilder criteriaBuilder, Path<?> path, Object value, boolean ignoreCase) {}
