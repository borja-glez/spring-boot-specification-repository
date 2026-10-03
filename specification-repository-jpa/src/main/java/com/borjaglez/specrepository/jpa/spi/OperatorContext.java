package com.borjaglez.specrepository.jpa.spi;

import static org.apiguardian.api.API.Status.MAINTAINED;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record OperatorContext(
    CriteriaBuilder criteriaBuilder, Path<?> path, Object value, boolean ignoreCase) {}
