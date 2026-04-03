package com.borjaglez.specrepository.core;

import java.util.List;

import org.springframework.data.domain.Sort;

public record QueryPlan<T>(
    Class<T> entityType,
    GroupCondition rootCondition,
    List<JoinInstruction> joins,
    List<FetchInstruction> fetches,
    List<String> projections,
    List<String> groupBy,
    Sort sort,
    boolean distinct) {}
