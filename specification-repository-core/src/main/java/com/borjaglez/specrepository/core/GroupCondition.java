package com.borjaglez.specrepository.core;

import java.util.List;

public record GroupCondition(LogicalOperator logicalOperator, List<QueryCondition> conditions)
    implements QueryCondition {}
