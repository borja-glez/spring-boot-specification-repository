package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record PredicateCondition(
    String field, FilterOperator operator, Object value, boolean ignoreCase, boolean includeNulls)
    implements QueryCondition {}
