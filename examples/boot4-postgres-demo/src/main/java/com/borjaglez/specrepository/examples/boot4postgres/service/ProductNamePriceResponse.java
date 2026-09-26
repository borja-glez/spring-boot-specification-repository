package com.borjaglez.specrepository.examples.boot4postgres.service;

import java.math.BigDecimal;

public record ProductNamePriceResponse(String name, BigDecimal price) {}
