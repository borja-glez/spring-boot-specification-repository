package com.borjaglez.specrepository.examples.boot4postgres.service;

import java.math.BigDecimal;

public record ProductAggregateSummaryResponse(
    BigDecimal totalActivePrice,
    Double averageActivePrice,
    BigDecimal cheapestActivePrice,
    BigDecimal mostExpensiveActivePrice,
    long activeProductsWithDescription) {}
