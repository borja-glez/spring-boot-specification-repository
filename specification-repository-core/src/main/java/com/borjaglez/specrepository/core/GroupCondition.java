package com.borjaglez.specrepository.core;

import static org.apiguardian.api.API.Status.MAINTAINED;

import java.util.List;

import org.apiguardian.api.API;

@API(status = MAINTAINED, since = "1.0.0")
public record GroupCondition(LogicalOperator logicalOperator, List<QueryCondition> conditions)
    implements QueryCondition {}
