package com.borjaglez.specrepository.core;

public record PredicateCondition(
    String field, FilterOperator operator, Object value, boolean ignoreCase, boolean includeNulls)
    implements QueryCondition {}
